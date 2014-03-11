// -------------------------------
// adapted from Kevin T. Manley
// modified by Muntasir M. Chowdhury
// CSE 593
//
package ResImpl;

import LockManager.*;
import ResInterface.*;

import java.util.*;
import java.rmi.registry.Registry;
import java.rmi.registry.LocateRegistry;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.rmi.RMISecurityManager;
import java.io.*;
import java.net.*;

public class MiddlewareImpl{
	ServerSocket serverSock;
	int port = 3220;
	LockManager lm = new LockManager ();//only one version for the whole Middleware
	HashMap<Integer, Stack<TransactionObj>> transHash = new HashMap(); //used for transaction management
	HashMap<Integer, Integer> tidBase = new HashMap();
	public MiddlewareImpl(){
		timeout tme = new timeout();
		tme.start();
		try{
        	serverSock = new ServerSocket(port);
        }
        catch(Exception e){
        	e.printStackTrace();
        }
		System.out.println("Middleware Online.");
		while(true){
			try{
				Socket connection = serverSock.accept();
				MiddlewareImpl2 mwd = new MiddlewareImpl2(connection, lm, transHash, tidBase);
				mwd.start();
			}
			catch(Exception e){
				e.printStackTrace();			
			}
		}
	}
	public static void main(String args[]) {
         MiddlewareImpl mw = new MiddlewareImpl();
    }
	
    public class timeout extends Thread{
    	public timeout(){}
    	public void run(){
    		try{
    			while(true){
        			Thread.sleep(1000);
        			Iterator<Map.Entry<Integer, Integer>> it = tidBase.entrySet().iterator();

        			while (it.hasNext()) {
        			  Map.Entry<Integer, Integer> entry = it.next();
        			  int TID = entry.getKey();
        			  int tme = entry.getValue();
        			  if( tme - 1000 <= 0 ){
        				  Socket connection = null;
        				  MiddlewareImpl2 mwd = new MiddlewareImpl2(connection, lm, transHash, tidBase);
        				  mwd.abort(TID);
				          System.out.println("Transaction " + TID + " timed out.");
				      }
        			  else 
        				  entry.setValue(tme - 1000);
        			 }
        		}
    		}catch(Exception e){
    			e.printStackTrace();
    		}
    	}
    }
}

class MiddlewareImpl2 extends Thread//implements Middleware 
{
    static protected RMHashtable m_itemHT = new RMHashtable();
    //Variables related to connection to client [Middleware works as server]
    ObjectOutputStream outputClient;
	ObjectInputStream inputClient;
	//ServerSocket serverSock;
	Socket connection = null;
	
    //Variable related to connection to RM [Middleware works as client]
    String serverRMFlight = "localhost", serverRMCar = "localhost", serverRMHotel = "localhost";
    int portRMFlight = 3221, portRMCar = 3222, portRMHotel = 3223;
    String RMNames[] = {"Flight", "Car", "Hotel"};
    Socket clientSock;
	ObjectOutputStream outputRM;
	ObjectInputStream inputRM;
	TransactionManager TM = new TransactionManager();
	HashMap<Integer, Stack<TransactionObj>> transHash = new HashMap(); //used for transaction management
	HashMap<Integer, Integer> tidBase = new HashMap();
	
	LockManager lm;
	public MiddlewareImpl2( Socket s, LockManager lmm, HashMap<Integer, Stack<TransactionObj>> transHashH, HashMap<Integer, Integer> tidBaseB  ){
		connection = s;
		this.lm = lmm;
		this.transHash = transHashH;
		this.tidBase = tidBaseB;
	}
	//Connects to RM. For queries that only require boolean based replies
	protected boolean forwardGetBooleanReply( String msgtcp, int RMType, int TID ){ 
		String messageIn = "", server;
		int port;
		//Choose which RM to connect to
		if( RMType == 0 ){
			server = serverRMFlight; port = portRMFlight;
		}
		else if( RMType == 1 ){
			server = serverRMCar; port = portRMCar;
		}
		else {
			server = serverRMHotel; port = portRMHotel;
		}
		
		try{
        	clientSock = new Socket( server , port );
        	outputRM = new ObjectOutputStream(clientSock.getOutputStream());
			outputRM.flush();
			inputRM = new ObjectInputStream(clientSock.getInputStream());
			messageIn = (String)inputRM.readObject();
			if( messageIn.equals("Connection Successful")){
				outputRM.writeObject( msgtcp );//this sends the msg
				outputRM.flush();
				messageIn = (String)inputRM.readObject();
			}
			else messageIn = "false";
		} 
        catch (Exception e){   
        	System.err.println("Middleware exception with RM [Boolean Reply]: " + e.toString());
        	e.printStackTrace();
        	Socket connection = null;
			MiddlewareImpl2 mwd = new MiddlewareImpl2(connection, lm, transHash, tidBase);
			mwd.abort(TID);
			System.out.println("FAILURE ERROR: Transaction cancelled at " + RMNames[RMType] + "RM due to crash/time out.");
        }
		finally{
			//4. Closing connection
			try{
				//Close streams
				inputRM.close();
				outputRM.close();
				//Close socket
				clientSock.close();
			}
			catch(Exception e){
				System.err.println(e);
				Socket connection = null;
				MiddlewareImpl2 mwd = new MiddlewareImpl2(connection, lm, transHash, tidBase);
				mwd.abort(TID);
				System.out.println("FAILURE ERROR: Transaction cancelled at " + RMNames[RMType] + "RM due to crash/time out.");
			}
		}
		if( messageIn.equals("true") ) return true;
		else if( messageIn.equals("timedout") ){
			Socket connection = null;
			MiddlewareImpl2 mwd = new MiddlewareImpl2(connection, lm, transHash, tidBase);
			mwd.abort(TID);
			System.out.println("FAILURE ERROR: Transaction cancelled at " + RMNames[RMType] + "RM due to crash/time out.");
			return false;
		}
		else return false;
	}

	//Connects to RM. For queries that require STRING replies
	private String forwardGetStringReply( String msgtcp, int RMType, int TID ){ 
		String messageIn = "", server;
		int port;
		//Choose which RM to connect to
		if( RMType == 0 ){
			server = serverRMFlight; port = portRMFlight;
		}
		else if( RMType == 1 ){
			server = serverRMCar; port = portRMCar;
		}
		else {
			server = serverRMHotel; port = portRMHotel;
		}
		
		try{
        	clientSock = new Socket( server , port );
        	//clientSock.setSoTimeout(500);
        	outputRM = new ObjectOutputStream(clientSock.getOutputStream());
			outputRM.flush();
			inputRM = new ObjectInputStream(clientSock.getInputStream());
			messageIn = (String)inputRM.readObject();
			if( messageIn.equals("Connection Successful")){
				outputRM.writeObject( msgtcp );//this sends the msg
				outputRM.flush();
				messageIn = (String)inputRM.readObject();
			}
			else messageIn = "false";
			if( messageIn.equals("timedout") ){ 
				Socket connection = null;
				MiddlewareImpl2 mwd = new MiddlewareImpl2(connection, lm, transHash, tidBase);
				mwd.abort(TID);
				System.out.println("FAILURE ERROR: Transaction cancelled at " + RMNames[RMType] + "RM due to crash/time out.");
			}
		} 
        catch (Exception e){    
            System.err.println("Middleware exception with RM [StringReply]: " + e.toString());
            e.printStackTrace();
            Socket connection = null;
			MiddlewareImpl2 mwd = new MiddlewareImpl2(connection, lm, transHash, tidBase);
			mwd.abort(TID);
			System.out.println("FAILURE ERROR: Transaction cancelled at " + RMNames[RMType] + "RM due to crash/time out.");
            return "false";
        }
		finally{
			//4. Closing connection
			try{
				//Close streams
				inputRM.close();
				outputRM.close();
				//Close socket
				clientSock.close();
			}
			catch(Exception e){
				System.err.println(e);
				Socket connection = null;
				MiddlewareImpl2 mwd = new MiddlewareImpl2(connection, lm, transHash, tidBase);
				mwd.abort(TID);
				System.out.println("FAILURE ERROR: Transaction cancelled at " + RMNames[RMType] + "RM due to crash/time out.");
			}
		}
		return messageIn;
	}
	
	public void run(){
		//Act as server for clients
		try{
	 		//3. Get Input and Output Streams
			outputClient = new ObjectOutputStream(connection.getOutputStream());
			outputClient.flush();
			inputClient = new ObjectInputStream(connection.getInputStream());
			sendMessage("Connection Successful");
			
			//4. Communication with client
			try{
				String msgReq = (String)inputClient.readObject(); //read msg from client
				System.out.println("client>" + msgReq); //to keep track of who's saying what
				//Parse message and act accordingly
				Vector arguments = new Vector();
				arguments = parse( msgReq );
				
				//decide which of the commands this was
				int flightNum, flightPrice, flightSeats, Id, Cid, numCars, numRooms, price, TID;
				String location;
		        switch(findChoice((String)arguments.elementAt(0))){
			        case 2:  //new flight
			            //if(arguments.size()!=5)break;
			            
			            System.out.println("Adding a new Flight using id: "+arguments.elementAt(1));
			            System.out.println("Flight number: "+arguments.elementAt(2));
			            System.out.println("Add Flight Seats: "+arguments.elementAt(3));
			            System.out.println("Set Flight Price: "+arguments.elementAt(4));
			            
			            try{
			            Id = getInt(arguments.elementAt(1));
			            flightNum = getInt(arguments.elementAt(2));
			            flightSeats = getInt(arguments.elementAt(3));
			            flightPrice = getInt(arguments.elementAt(4));
			            TID = getInt(arguments.elementAt(5));
			            if(tidBase.get(TID) == null){
			            	sendMessage("timedout");
			            	break;
			            }			            	
			            String tcpMsg = "newflight," + Id + "," + flightNum + "," + flightSeats + "," + flightPrice + "," + TID;
			            if( forwardGetBooleanReply( tcpMsg, 0, TID ) )
			                sendMessage("true");
			            else
			                sendMessage("false");
			            }
			            catch(Exception e){
				            System.out.println("EXCEPTION:");
				            System.out.println(e.getMessage());
				            e.printStackTrace();
			            }
			            break;
			            
			        case 3:  //new Car
			            System.out.println("Adding a new Car using id: "+arguments.elementAt(1));
			            System.out.println("Car Location: "+arguments.elementAt(2));
			            System.out.println("Add Number of Cars: "+arguments.elementAt(3));
			            System.out.println("Set Price: "+arguments.elementAt(4));
			            try{
			            Id = getInt(arguments.elementAt(1));
			            location = getString(arguments.elementAt(2));
			            numCars = getInt(arguments.elementAt(3));
			            price = getInt(arguments.elementAt(4));
			            TID = getInt(arguments.elementAt(5));
			            if(tidBase.get(TID) == null){
			            	sendMessage("timedout");
			            	break;
			            }
			            String tcpMsg = "newcar," + Id + "," + location + "," + numCars + "," + price + "," + TID;
			            if( forwardGetBooleanReply( tcpMsg, 1, TID ) )
			                sendMessage("true");
			            else
			                sendMessage("false");
			            }
			            catch(Exception e){
			            System.out.println("EXCEPTION:");
			            System.out.println(e.getMessage());
			            e.printStackTrace();
			            }
			            break;
			            
			        case 4:  //new Room
			            System.out.println("Adding a new Room using id: "+arguments.elementAt(1));
			            System.out.println("Room Location: "+arguments.elementAt(2));
			            System.out.println("Add Number of Rooms: "+arguments.elementAt(3));
			            System.out.println("Set Price: "+arguments.elementAt(4));
			            try{
			            Id = getInt(arguments.elementAt(1));
			            location = getString(arguments.elementAt(2));
			            numRooms = getInt(arguments.elementAt(3));
			            price = getInt(arguments.elementAt(4));
			            TID = getInt(arguments.elementAt(5));
			            if(tidBase.get(TID) == null){
			            	sendMessage("timedout");
			            	break;
			            }
			            String tcpMsg = "newroom," + Id + "," + location + "," + numRooms + "," + price + "," + TID;
			            if( forwardGetBooleanReply( tcpMsg, 2, TID ) )
			                sendMessage("true");
			            else
			                sendMessage("false");
			            }
			            catch(Exception e){
			            System.out.println("EXCEPTION:");
			            System.out.println(e.getMessage());
			            e.printStackTrace();
			            }
			            break;
			            
			        case 5:  //new Customer
			            System.out.println("Adding a new Customer using id:"+arguments.elementAt(1));
			            try{
			            	Id = getInt(arguments.elementAt(1));
			            	TID = getInt(arguments.elementAt(2));
			            	if(tidBase.get(TID) == null){
				            	sendMessage("timedout");
				            	break;
				            }
			            	String tcpMsg = "true," + String.valueOf( newCustomer( Id, TID ) );
			            	sendMessage(tcpMsg);
			            }
			            catch(Exception e){
			            	System.out.println("EXCEPTION:");
			            	System.out.println(e.getMessage());
			            	e.printStackTrace();
			            }
			            break;
			            
			        case 6: //delete Flight
			            System.out.println("Deleting a flight using id: "+arguments.elementAt(1));
			            System.out.println("Flight Number: "+arguments.elementAt(2));
			            try{
			            Id = getInt(arguments.elementAt(1));
			            flightNum = getInt(arguments.elementAt(2));
			            TID = getInt(arguments.elementAt(3));
			            if(tidBase.get(TID) == null){
			            	sendMessage("timedout");
			            	break;
			            }
			            String tcpMsg = "deleteFlight," + Id + "," + flightNum + "," + TID;
			            if( forwardGetBooleanReply( tcpMsg, 0, TID ) )
			                sendMessage("true");
			            else
			                sendMessage("false");
			            }
			            catch(Exception e){
			            System.out.println("EXCEPTION:");
			            System.out.println(e.getMessage());
			            e.printStackTrace();
			            }
			            break;
			            
			        case 7: //delete Car
			            System.out.println("Deleting the cars from a particular location  using id: "+arguments.elementAt(1));
			            System.out.println("Car Location: "+arguments.elementAt(2));
			            try{
			            Id = getInt(arguments.elementAt(1));
			            location = getString(arguments.elementAt(2));
			            TID = getInt(arguments.elementAt(3));
			            if(tidBase.get(TID) == null){
			            	sendMessage("timedout");
			            	break;
			            }
			            String tcpMsg = "deleteCar," + Id + "," + location + "," + TID;
			            if( forwardGetBooleanReply( tcpMsg, 1, TID ) )
			                sendMessage("true");
			            else
			                sendMessage("false");
			            }
			            catch(Exception e){
			            System.out.println("EXCEPTION:");
			            System.out.println(e.getMessage());
			            e.printStackTrace();
			            }
			            break;
			            
			        case 8: //delete Room
			            System.out.println("Deleting all rooms from a particular location  using id: "+arguments.elementAt(1));
			            System.out.println("Room Location: "+arguments.elementAt(2));
			            try{
			            Id = getInt(arguments.elementAt(1));
			            location = getString(arguments.elementAt(2));
			            TID = getInt(arguments.elementAt(3));
			            if(tidBase.get(TID) == null){
			            	sendMessage("timedout");
			            	break;
			            }
			            String tcpMsg = "deleteRoom," + Id + "," + location + "," + TID;
			            if( forwardGetBooleanReply( tcpMsg, 2, TID ) )
			                sendMessage("true");
			            else
			                sendMessage("false");
			            }
			            catch(Exception e){
			            System.out.println("EXCEPTION:");
			            System.out.println(e.getMessage());
			            e.printStackTrace();
			            }
			            break;
			            
			        case 9: //delete Customer
			            System.out.println("Deleting a customer from the database using id: "+arguments.elementAt(1));
			            System.out.println("Customer id: "+arguments.elementAt(2));
			            try{
			            Id = getInt(arguments.elementAt(1));
			            int customer = getInt(arguments.elementAt(2));
			            TID = getInt(arguments.elementAt(3));
			            if(tidBase.get(TID) == null){
			            	sendMessage("timedout");
			            	break;
			            }
			            if( deleteCustomer( Id, customer, TID ) )
			            	sendMessage( "true" );
			            else
			            	sendMessage( "false" );
			            }
			            catch(Exception e){
			            System.out.println("EXCEPTION:");
			            System.out.println(e.getMessage());
			            e.printStackTrace();
			            }
			            break;
			            
			        case 10: //querying a flight
			            System.out.println("Querying a flight using id: "+arguments.elementAt(1));
			            System.out.println("Flight number: "+arguments.elementAt(2));
			            try{
				            Id = getInt(arguments.elementAt(1));
				            flightNum = getInt(arguments.elementAt(2));
				            TID = getInt(arguments.elementAt(3));
				            if(tidBase.get(TID) == null){
				            	sendMessage("timedout");
				            	break;
				            }
				            String tcpMsg = "queryFlight," + Id + "," + flightNum + "," + TID;
				            String status = forwardGetStringReply( tcpMsg, 0, TID );
				            status = forwardGetStringReply( tcpMsg, 0, TID );
					        sendMessage( status );
				        }
			            catch(Exception e){
			            System.out.println("EXCEPTION:");
			            System.out.println(e.getMessage());
			            e.printStackTrace();
			            }
			            break;
			            
			        case 11: //querying a Car Location
			            System.out.println("Querying a car location using id: "+arguments.elementAt(1));
			            System.out.println("Car location: "+arguments.elementAt(2));
			            try{
			            Id = getInt(arguments.elementAt(1));
			            location = getString(arguments.elementAt(2));
			            TID = getInt(arguments.elementAt(3));
			            if(tidBase.get(TID) == null){
			            	sendMessage("timedout");
			            	break;
			            }
			            String tcpMsg = "queryCar," + Id + "," + location + "," + TID;
			            String status = forwardGetStringReply( tcpMsg, 1, TID );
				        sendMessage( status );
				        }
			            catch(Exception e){
			            System.out.println("EXCEPTION:");
			            System.out.println(e.getMessage());
			            e.printStackTrace();
			            }
			            break;
			            
			        case 12: //querying a Room location
			            System.out.println("Querying a room location using id: "+arguments.elementAt(1));
			            System.out.println("Room location: "+arguments.elementAt(2));
			            try{
			            Id = getInt(arguments.elementAt(1));
			            location = getString(arguments.elementAt(2));
			            TID = getInt(arguments.elementAt(3));
			            if(tidBase.get(TID) == null){
			            	sendMessage("timedout");
			            	break;
			            }
			            String tcpMsg = "queryRoom," + Id + "," + location + "," + TID;
			            String status = forwardGetStringReply( tcpMsg, 2, TID );
				        sendMessage( status );
			            }
			            catch(Exception e){
			            System.out.println("EXCEPTION:");
			            System.out.println(e.getMessage());
			            e.printStackTrace();
			            }
			            break;
			            
			        case 13: //querying Customer Information
			            System.out.println("Querying Customer information using id: "+arguments.elementAt(1));
			            System.out.println("Customer id: "+arguments.elementAt(2));
			            try{
				            Id = getInt(arguments.elementAt(1));
				            int customer = getInt(arguments.elementAt(2));
				            TID = getInt(arguments.elementAt(3));
				            if(tidBase.get(TID) == null){
				            	sendMessage("timedout");
				            	break;
				            }
				            String status = queryCustomerInfo( Id, customer, TID ); 
				            if( status.equals("") ) 
					            sendMessage("false");
					        else
					        	sendMessage("true," + status);
			            }
			            catch(Exception e){
			            System.out.println("EXCEPTION:");
			            System.out.println(e.getMessage());
			            e.printStackTrace();
			            }
			            break;               
			            
			        case 14: //querying a flight Price
			            System.out.println("Querying a flight Price using id: "+arguments.elementAt(1));
			            System.out.println("Flight number: "+arguments.elementAt(2));
			            try{
			            Id = getInt(arguments.elementAt(1));
			            flightNum = getInt(arguments.elementAt(2));
			            TID = getInt(arguments.elementAt(3));
			            if(tidBase.get(TID) == null){
			            	sendMessage("timedout");
			            	break;
			            }
			            String tcpMsg = "queryFlightPrice," + Id + "," + flightNum + "," + TID;
			            String status = forwardGetStringReply( tcpMsg, 0, TID );
				        sendMessage( status );
			            }
			            catch(Exception e){
			            	System.out.println("EXCEPTION:");
			            	System.out.println(e.getMessage());
			            	e.printStackTrace();
			            }
			            break;
			            
			        case 15: //querying a Car Price
			            System.out.println("Querying a car price using id: "+arguments.elementAt(1));
			            System.out.println("Car location: "+arguments.elementAt(2));
			            try{
			            	Id = getInt(arguments.elementAt(1));
			            	location = getString(arguments.elementAt(2));
			            	TID = getInt(arguments.elementAt(3));
			            	if(tidBase.get(TID) == null){
				            	sendMessage("timedout");
				            	break;
				            }
			            	String tcpMsg = "queryCarPrice," + Id + "," + location + "," + TID;
			            	String status = forwardGetStringReply( tcpMsg, 1, TID );
					        sendMessage( status );
			            }
			            catch(Exception e){
			            System.out.println("EXCEPTION:");
			            System.out.println(e.getMessage());
			            e.printStackTrace();
			            }                
			            break;
			
			        case 16: //querying a Room price
			            /*if(arguments.size()!=3){
			            
			            break;
			            }*/
			            System.out.println("Querying a room price using id: "+arguments.elementAt(1));
			            System.out.println("Room Location: "+arguments.elementAt(2));
			            try{
			            Id = getInt(arguments.elementAt(1));
			            location = getString(arguments.elementAt(2));
			            TID = getInt(arguments.elementAt(3));
			            if(tidBase.get(TID) == null){
			            	sendMessage("timedout");
			            	break;
			            }
			            String tcpMsg = "queryRoomPrice," + Id + "," + location + "," + TID;
			            String status = forwardGetStringReply( tcpMsg, 2, TID );
				        sendMessage( status );
			            }
			            catch(Exception e){
			            System.out.println("EXCEPTION:");
			            System.out.println(e.getMessage());
			            e.printStackTrace();
			            }
			            break;
			            
			        case 17:  //reserve a flight
			            System.out.println("Reserving a seat on a flight using id: "+arguments.elementAt(1));
			            System.out.println("Customer id: "+arguments.elementAt(2));
			            System.out.println("Flight number: "+arguments.elementAt(3));
			            try{
			            Id = getInt(arguments.elementAt(1));
			            int customer = getInt(arguments.elementAt(2));
			            flightNum = getInt(arguments.elementAt(3));
			            TID = getInt(arguments.elementAt(4));
			            if(tidBase.get(TID) == null){
			            	sendMessage("timedout");
			            	break;
			            }
			            if( reserveFlight( Id, customer, flightNum, TID ) )
			            	sendMessage("true");
			            else
			                sendMessage("false");
			            }
			            catch(Exception e){
			            System.out.println("EXCEPTION:");
			            System.out.println(e.getMessage());
			            e.printStackTrace();
			            }
			            break;
			            
			        case 18:  //reserve a car
			            System.out.println("Reserving a car at a location using id: "+arguments.elementAt(1));
			            System.out.println("Customer id: "+arguments.elementAt(2));
			            System.out.println("Location: "+arguments.elementAt(3));
			            
			            try{
			            Id = getInt(arguments.elementAt(1));
			            int customer = getInt(arguments.elementAt(2));
			            location = getString(arguments.elementAt(3));
			            TID = getInt(arguments.elementAt(4));
			            if(tidBase.get(TID) == null){
			            	sendMessage("timedout");
			            	break;
			            }
			            if( reserveCar( Id, customer, location, TID ) )
			            	sendMessage("true");
			            else
			                sendMessage("false");
			            }
			            catch(Exception e){
			            System.out.println("EXCEPTION:");
			            System.out.println(e.getMessage());
			            e.printStackTrace();
			            }
			            break;
			            
			        case 19:  //reserve a room
			            System.out.println("Reserving a room at a location using id: "+arguments.elementAt(1));
			            System.out.println("Customer id: "+arguments.elementAt(2));
			            System.out.println("Location: "+arguments.elementAt(3));
			            try{
			            Id = getInt(arguments.elementAt(1));
			            int customer = getInt(arguments.elementAt(2));
			            location = getString(arguments.elementAt(3));
			            TID = getInt(arguments.elementAt(4));
			            if(tidBase.get(TID) == null){
			            	sendMessage("timedout");
			            	break;
			            }
			            if( reserveRoom( Id, customer, location, TID ) )
			            	sendMessage("true");
			            else
			                sendMessage("false");
			            }
			            catch(Exception e){
			            System.out.println("EXCEPTION:");
			            System.out.println(e.getMessage());
			            e.printStackTrace();
			            }
			            break;
			            
			        case 20:  //reserve an Itinerary
			            
			            //Abort: will need to something in the middle of this
			            
			            System.out.println("Reserving an Itinerary using id:"+arguments.elementAt(1));
			            System.out.println("Customer id:"+arguments.elementAt(2));
			            for(int i=0;i<arguments.size()-6;i++)
			            System.out.println("Flight number"+arguments.elementAt(3+i));
			            System.out.println("Location for Car/Room booking:"+arguments.elementAt(arguments.size()-4));
			            System.out.println("Car to book?:"+arguments.elementAt(arguments.size()-3));
			            System.out.println("Room to book?:"+arguments.elementAt(arguments.size()-2));
			            System.out.println("Transaction Number:"+arguments.elementAt(arguments.size()-1));
			            try{
			            Id = getInt(arguments.elementAt(1));
			            Cid = getInt(arguments.elementAt(2));
			            Vector flightNumbers = new Vector();
			            boolean confirmation = true;
			            TID = getInt(arguments.elementAt(arguments.size()-1));
			            if(tidBase.get(TID) == null){
			            	sendMessage("timedout");
			            	break;
			            }
			            for(int i=0;i<arguments.size()-6;i++){
			                confirmation = confirmation & reserveFlight( Id, Cid, getInt( arguments.elementAt(3+i) ), TID );
			            }
			            location = getString(arguments.elementAt(arguments.size()-4));
			            int Ccar = getInt(arguments.elementAt(arguments.size()-3));
			            int Rroom = getInt(arguments.elementAt(arguments.size()-2));
			            if( Ccar != 0 ) confirmation = confirmation & reserveCar( Id, Cid, location, TID );
			            if( Rroom != 0 ) confirmation = confirmation & reserveRoom( Id, Cid, location, TID );
			            if( confirmation )
			                sendMessage("true");
			            else
			            	sendMessage("false");//("Itinerary could not be reserved.");
			                
			            }
			            catch(Exception e){
			            System.out.println("EXCEPTION:");
			            System.out.println(e.getMessage());
			            e.printStackTrace();
			            }
			            break;
			                        
			        case 21:  //quit the client
			            System.out.println("Quitting client.");
			            System.exit(1);
			            
			            
			        case 22:  //new Customer given id
			            try{
			            	Id = getInt(arguments.elementAt(1));
			            	Cid = getInt(arguments.elementAt(2));
			            	TID = getInt(arguments.elementAt(3));
			            	if(tidBase.get(TID) == null){
				            	sendMessage("timedout");
				            	break;
				            }
			            	if( newCustomer( Id, Cid, TID ) )
			            		sendMessage("true");
			            	else
			            		sendMessage("false");
			            }
			            catch(Exception e){
			            	System.out.println("EXCEPTION:");
			            	System.out.println(e.getMessage());
			            	e.printStackTrace();
			            }
			            break;
			        
			        case 23:  //start a new transaction
			        				        	
			        	//send string "start" to Middleware
			        	int tmpTID = 0;
			        	boolean flagTID = false;
			        	while( flagTID == false ){
			        		//In case the MW fails:
			        		//creating new TIDs until all past TIDs at RMs are cleared 
				        	tmpTID = TM.start();
				        	tidBase.put(tmpTID, 120000);//TimeOut Amount
				        	System.out.println("MW::Starting a new transaction with ID: " + tmpTID);
				        	String msgtcp = "start," + tmpTID;
				        	flagTID = forwardGetBooleanReply(msgtcp, 0, 0);//here sending TID doesn't matter
				        	flagTID &= forwardGetBooleanReply(msgtcp, 1, 0);
				        	flagTID &= forwardGetBooleanReply(msgtcp, 2, 0);
				        	if(flagTID == false){
				        		tidBase.remove(tmpTID);
				        		System.out.println("GENERATED Transaction ID is redundant. Trying again.");
				        	}
				        	else
				        		System.out.println("NEW Transaction ID: " + tmpTID + ".");
			        	}
			            try{
			            	sendMessage("true," + String.valueOf(tmpTID));
			            }
			            catch(Exception e){
			            	System.out.println("EXCEPTION:");
			            	System.out.println(e.getMessage());
			            	e.printStackTrace();
			            }
		                break;
			        
			        case 24: //commits transaction
			        	//send string "commit" to RM
			        	try{
				            Id = getInt(arguments.elementAt(1));
				            TID = getInt(arguments.elementAt(2));
				            String crashString = getString(arguments.elementAt(3));
				            int crashCon = Integer.parseInt(crashString.substring(0,1));
				            if(tidBase.get(TID) == null){
				            	sendMessage("timedout");
				            	break;
				            }
				            /////IMPLEMENT 2PC HERE////////
				            String tcpMsg = "voteRequest," + Id + "," + TID + "," + crashCon;
				            boolean[] votes = new boolean[3];
				            boolean flag = true;
				            int i;
				            //CP1 - before sending vote request
				            if(crashCon == 1){
				            	System.out.println("CRASHING before sending vote request.");
				            	System.exit(1);
				            }
				            for( i = 0; i < 3; i++ ){
				            	votes[i] = forwardGetBooleanReply( tcpMsg, i, TID );
				            	if( votes[i] )
				            		System.out.println("YES vote at RM " + i + ".");				            		
				            	else{
				            		System.out.println("NO vote at RM " + i + ".");
				            		flag = false;
				            	}
				            	//CP2 - after one vote and reply
				            	if(crashCon == 2){
				            		System.out.println("CRASHING before after sending one vote request.");
				            		System.exit(1);
				            	}
				            	//optimization would be to break here
				            }
				            //CP3 - before deciding
				            if(crashCon == 3){
				            	System.out.println("CRASHING before deciding.");
				            	System.exit(1);
				            }
				            //send final decision
				            if( flag == false ){
				            	//abort locally first
				            	System.out.println("ABORTING all.");
				            	abort(TID);//send a parameter signifying different type of abort
					            /*Send Abort to all who voted YES
				            	tcpMsg = "abort," + Id + "," + TID;
				            	for( i = 0; i < 3; i++ )
				            		if( votes[i] ) forwardGetBooleanReply( tcpMsg, i );*/
				            	sendMessage("false");
				            }
				            else{
				            	tcpMsg = "commit," + Id + "," + TID;
				            	for( i = 0; i < 3; i++ ){
				            		if( forwardGetBooleanReply( tcpMsg, i, TID ) )
				            			System.out.println("Committed Transaction at RM" + i + ".");
				            		else
				            			System.out.println("Failed to commit Transaction at RM" + i + ".");
				            		//CP4 - After sending some decisions
				            		if(crashCon == 4){
				            			System.out.println("CRASHING after sending decision to one RM.");
				            			System.exit(1);
				            		}
					            }
				            	lm.UnlockAll(TID);
					            transHash.remove(TID);
				            	//CP5 - after sending all decisions
					            if(crashCon == 5){
					            	System.out.println("CRASHING after sending decision to all.");
					            	System.exit(1);
					            }
				            	sendMessage("true");
					        }
				            tidBase.remove(TID);
				         }
			        	 catch(Exception e){
					            System.out.println("EXCEPTION:");
					            System.out.println(e.getMessage());
					            e.printStackTrace();
					            sendMessage("false");//should do for all exceptions exit
					     }
			        	break;
			            
			        case 25: //aborts transaction
			        	//send abort command to RM
			        	try{
				            Id = getInt(arguments.elementAt(1));
				            TID = getInt(arguments.elementAt(2));
				            if(tidBase.get(TID) == null){
				            	sendMessage("timedout");
				            	break;
				            }
				            abort(TID);
				            sendMessage("true");				            
				           }
				            catch(Exception e){
					            System.out.println("EXCEPTION:");
					            System.out.println(e.getMessage());
					            e.printStackTrace();
				            }
			        	break;
			            
			        default:
			            System.out.println("The interface does not support this command.");
			            break;
			        
			        }//end of switch
			}
			catch(Exception e){
				System.err.println(e);
			}
		}
		catch(Exception e){
			System.err.println(e);
		}
		finally{
			//Close socket
			try{
				//Close I/O Streams
				inputClient.close();
				outputClient.close();
			}
			catch(Exception e){
				System.err.println(e);
			}
		}
	}
	
	void sendMessage(String msg){
		try{
			outputClient.writeObject(msg);
			outputClient.flush();
			System.out.println("Middleware To Client > " + msg); //This is just to keep track of who's saying what
		}
		catch(Exception e){
			System.err.println(e);
		}
	}
	
    // Reads a data item
    private RMItem readData( int id, String key )
    {
        synchronized(m_itemHT) {
            return (RMItem) m_itemHT.get(key);
        }
    }

    // Writes a data item
    private void writeData( int id, String key, RMItem value )
    {
        synchronized(m_itemHT) {
            m_itemHT.put(key, value);
        }
    }
    
    // Remove the item out of storage
    protected RMItem removeData(int id, String key) {
        synchronized(m_itemHT) {
            return (RMItem)m_itemHT.remove(key);
        }
    }
    
    
    // deletes the entire item
    protected boolean deleteItem(int id, String key)
    {
        Trace.info("RM::deleteItem(" + id + ", " + key + ") called" );
        ReservableItem curObj = (ReservableItem) readData( id, key );
        // Check if there is such an item in the storage
        if ( curObj == null ) {
            Trace.warn("RM::deleteItem(" + id + ", " + key + ") failed--item doesn't exist" );
            return false;
        } else {
            if (curObj.getReserved()==0) {
                removeData(id, curObj.getKey());
                Trace.info("RM::deleteItem(" + id + ", " + key + ") item deleted" );
                return true;
            }
            else {
                Trace.info("RM::deleteItem(" + id + ", " + key + ") item can't be deleted because some customers reserved it" );
                return false;
            }
        } // if
    }

  
    // reserve an item
    protected boolean reserveItem(int id, int customerID, String key, String location, int type, int pTID ) {
        Trace.info("RM::reserveItem( " + id + ", customer=" + customerID + ", " +key+ ", "+location+" " + pTID + " ) called" );        
        // Read customer object if it exists (and read lock it)
        Customer cust = (Customer) readData( id, Customer.getKey(customerID) );
        try{
        	if( lm.Lock(pTID, Customer.getKey(customerID), 1 ) == false )
        		return false;
        }catch(DeadlockException d){
        	d.printStackTrace();
        	return false;
        }
        String callType;
        if( type == 0 ) callType = "reserveFlight";
    	else if( type == 1 ) callType = "reserveCar";
    	else callType = "reserveRoom";
        if ( cust == null ) {
        	Trace.warn("RM::+" + callType + "( " + id + ", " + customerID + ", " + key + ", "+location+")  failed--customer doesn't exist" );
        	return false;
        } 
        try{
        	String forwardRequest;
        	System.out.println("Calling:" + callType);
        	forwardRequest = callType + "," + id + "," + customerID + "," + location + "," + pTID;
        	//synchronized(cust){
        	if( forwardGetBooleanReply( forwardRequest, type, pTID ) ){//sends reserve request to RM
        		String tcpMsg;
        		if( type == 0 ) tcpMsg = "queryFlightPrice,";
        		else if( type == 1 ) tcpMsg = "queryCarPrice,";
        		else tcpMsg = "queryRoomPrice,";
        		tcpMsg = tcpMsg + id + "," + location + "," + pTID;///IMP: add pTID
                String price = forwardGetStringReply( tcpMsg, type, pTID );//queries RM about price of item
                String[] ps = price.split("[,]+");
                price = ps[1];
                ReservedItem item = cust.getReservedItem(key);
                
                //Abort: Write a copy of customer object
                Stack<TransactionObj> stack = getHashPos( pTID );
            	TransactionObj tobjz = new TransactionObj( 0, cust.getKey(), cust);
            	stack.push( tobjz );
                
            	Customer backup = new Customer(customerID, cust.m_Reservations);//custom constructor for deep copy
            	int finalprice = Integer.parseInt(price);
                if( item != null ) finalprice += item.getPrice();//Item already exists
                
                backup.reserve( key, location, finalprice  );//updates customer record
        		writeData( id, backup.getKey(), backup);//updates hashtable at Middleware
        		return true;
        	}
        	//}
        }
        catch (Exception e){
        	System.err.println(e);
        	e.printStackTrace();
        }
        return false;
    }
    

    public RMHashtable getCustomerReservations(int id, int customerID, int pTID)
        throws RemoteException, DeadlockException
    {
    	
        try{
        	if( lm.Lock( pTID, Customer.getKey(customerID), 0 ) ){
        		Trace.info("RM::getCustomerReservations(" + id + ", " + customerID + ") called" );
                Customer cust = (Customer) readData( id, Customer.getKey(customerID) );
                if ( cust == null ) {
                    Trace.warn("RM::getCustomerReservations failed(" + id + ", " + customerID + ") failed--customer doesn't exist" );
                    return null;
                } else {
                    return cust.getReservations();
                }
        	}        	
        }
        catch(DeadlockException d){
        	//d.printStackTrace();
        	throw d;
        }
        catch(Exception e){
        	throw e;
        }
        RMHashtable a = null;
        return a;
    }

    // return a bill
    public String queryCustomerInfo(int id, int customerID, int pTID)
        throws RemoteException
    {
        Trace.info("RM::queryCustomerInfo(" + id + ", " + customerID + ") called" );
        Customer cust = (Customer) readData( id, Customer.getKey(customerID) );
        if ( cust == null ) {
            Trace.warn("RM::queryCustomerInfo(" + id + ", " + customerID + ") failed--customer doesn't exist" );
            return "";   // NOTE: don't change this--WC counts on this value indicating a customer does not exist...
        } else {
        	try{
        		if( lm.Lock(pTID, Customer.getKey(customerID), 0 ) ){
	                String s = cust.printBill();
	                Trace.info("RM::queryCustomerInfo(" + id + ", " + customerID + "), bill follows..." );
	                System.out.println( s );
	                return s;
        		}
        	}
        	catch(DeadlockException d){
        		d.printStackTrace();
        		return "";
        	}
        	catch(Exception e){
        		e.printStackTrace();
        		return "";
        	}
        } // if
        return "";
    }

    // customer functions
    // new customer just returns a unique customer identifier
    
    public int newCustomer(int id, int pTID )
        throws RemoteException
    {
    	Trace.info("INFO: RM::newCustomer(" + id + ") called" );
        // Generate a globally unique ID for the new customer
        int cid = Integer.parseInt( String.valueOf(id) +
                                String.valueOf(Calendar.getInstance().get(Calendar.MILLISECOND)) +
                                String.valueOf( Math.round( Math.random() * 100 + 1 )));
        Customer cust = new Customer( cid );
        try{
        	lm.Lock( pTID, cust.getKey(), 1 );
        	//Abort: removeData on cust.getKey() and cust
        	Stack<TransactionObj> stack = getHashPos( pTID );
        	TransactionObj tobjz = new TransactionObj( 1, cust.getKey(), cust);
        	stack.push( tobjz );
        	
        	writeData( id, cust.getKey(), cust );
        	Trace.info("RM::newCustomer(" + cid + ") returns ID=" + cid );
        }
        catch(Exception e){
        	return -1;
        }
        return cid;
    }

    // I opted to pass in customerID instead. This makes testing easier
    public boolean newCustomer(int id, int customerID, int pTID)
        throws RemoteException
    {
        Trace.info("INFO: RM::newCustomer(" + id + ", " + customerID + ") called" );
        Customer cust = (Customer) readData( id, Customer.getKey(customerID) );
        if ( cust == null ) {
            cust = new Customer(customerID);
            try{
            	if( lm.Lock( pTID, cust.getKey(), 1 ) ){
            		//Abort: removeData on cust.getKey() and cust
            		Stack<TransactionObj> stack = getHashPos( pTID );
	            	TransactionObj tobjz = new TransactionObj( 1, cust.getKey(), cust);
	            	stack.push( tobjz );
	            	
	            	writeData( id, cust.getKey(), cust );
	                Trace.info("INFO: RM::newCustomer(" + id + ", " + customerID + ") created a new customer" );
	                return true;
            	}
            }
            catch(Exception e){
            	return false;
            }
            
        } else {
            Trace.info("INFO: RM::newCustomer(" + id + ", " + customerID + ") failed--customer already exists");
            return false;
        }
        return false;
    }


    // Deletes customer from the database. 
    public boolean deleteCustomer(int id, int customerID, int pTID)
        throws RemoteException
    {
        Trace.info("RM::deleteCustomer(" + id + ", " + customerID + ") called" );
        Customer cust = (Customer) readData( id, Customer.getKey(customerID) );
        if ( cust == null ) {
            Trace.warn("RM::deleteCustomer(" + id + ", " + customerID + ") failed--customer doesn't exist" );
            return false;
        } else {            
            // Increase the reserved numbers of all reservable items which the customer reserved. 
            RMHashtable reservationHT = cust.getReservations();
          //Abort: do something.....
          try{
        	  if( lm.Lock(pTID, cust.getKey(), 1) ){
        		  Stack<TransactionObj> stack = getHashPos( pTID );
              	  TransactionObj tobjz = new TransactionObj( 0, cust.getKey(), cust);
              	  stack.push( tobjz );
        		  
              	  for (Enumeration e = reservationHT.keys(); e.hasMoreElements();) {        
                      String reservedkey = (String) (e.nextElement());
                      ReservedItem reserveditem = cust.getReservedItem(reservedkey);
                      Trace.info("RM::deleteCustomer(" + id + ", " + customerID + ") has reserved " + reserveditem.getKey() + " " +  reserveditem.getCount() +  " times"  );
                      String resitemKey = reserveditem.getKey();
                      //parsing the key to get it ready for query to RM
                      String[] clean = resitemKey.split("[-]");
                      //resitemKey = clean[1]; depends on format e.g. flight-324 or 324
                      String tcpmsgQuery = "deleteRes,";
                      tcpmsgQuery = tcpmsgQuery + id + "," + resitemKey + "," + reserveditem.getCount() + "," + pTID;//adding the count at the end
                      int type;
                      if(clean[0].equals("flight")) type = 0;
                      else if(clean[0].equals("car")) type = 1;
                      else type = 2;
                      forwardGetBooleanReply( tcpmsgQuery, type, pTID );//updating corresponding number of released seats/cars/rooms at RMs
                  }
                  
                  // remove the customer from the storage
        		  removeData(id, cust.getKey());
                  Trace.info("RM::deleteCustomer(" + id + ", " + customerID + ") succeeded" );
                  return true;
        	  }
          }
          catch( DeadlockException d ){
        	  d.printStackTrace();
        	  return false;
          }
            
        } // if
        return false;
    }

    // Adds flight reservation to this customer.  
    public boolean reserveFlight(int id, int customerID, int flightNum, int pTID)
        throws RemoteException
    {
    	String s = "flight-"+flightNum;
        return reserveItem(id, customerID, s.toLowerCase(), String.valueOf(flightNum), 0, pTID);
    	//return rmf.reserveFlight(id, customerID, flightNum);
    	//return reserveItem(id, customerID, Flight.getKey(flightNum), String.valueOf(flightNum));
    }
    
    // Adds car reservation to this customer. 
    public boolean reserveCar(int id, int customerID, String location, int pTID)
        throws RemoteException
    {
    	String s = "car-"+location;
        return reserveItem(id, customerID, s.toLowerCase(), location, 1, pTID);
        //return reserveItem(id, customerID, Car.getKey(location), location, 2);
    }


    // Adds room reservation to this customer. 
    public boolean reserveRoom(int id, int customerID, String location, int pTID)
        throws RemoteException
    {
    	String s = "room-"+location;
    	return reserveItem(id, customerID, s.toLowerCase(), location, 2, pTID);
        //return reserveItem(id, customerID, Hotel.getKey(location), location, 3);
    }
    
    // Reserve an itinerary 
    public boolean itinerary(int id,int customer,Vector flightNumbers,String location,boolean Car,boolean Room)
        throws RemoteException{
        return false;
    }
   
    public int getInt(Object temp) throws Exception {
        try {
            return (new Integer((String)temp)).intValue();
            }
        catch(Exception e) {
            throw e;
            }
    } 
    public boolean getBoolean(Object temp) throws Exception {
        try {
            return (new Boolean((String)temp)).booleanValue();
            }
        catch(Exception e) {
            throw e;
            }
    }
    
    public String getString(Object temp) throws Exception {
	    try {    
	        return (String)temp;
	        }
	    catch (Exception e) {
	        throw e;
	        }
    }

    public int findChoice(String argument)
    {
	    if (argument.compareToIgnoreCase("help")==0)
	        return 1;
	    else if(argument.compareToIgnoreCase("newflight")==0)
	        return 2;
	    else if(argument.compareToIgnoreCase("newcar")==0)
	        return 3;
	    else if(argument.compareToIgnoreCase("newroom")==0)
	        return 4;
	    else if(argument.compareToIgnoreCase("newcustomer")==0)
	        return 5;
	    else if(argument.compareToIgnoreCase("deleteflight")==0)
	        return 6;
	    else if(argument.compareToIgnoreCase("deletecar")==0)
	        return 7;
	    else if(argument.compareToIgnoreCase("deleteroom")==0)
	        return 8;
	    else if(argument.compareToIgnoreCase("deletecustomer")==0)
	        return 9;
	    else if(argument.compareToIgnoreCase("queryflight")==0)
	        return 10;
	    else if(argument.compareToIgnoreCase("querycar")==0)
	        return 11;
	    else if(argument.compareToIgnoreCase("queryroom")==0)
	        return 12;
	    else if(argument.compareToIgnoreCase("querycustomer")==0)
	        return 13;
	    else if(argument.compareToIgnoreCase("queryflightprice")==0)
	        return 14;
	    else if(argument.compareToIgnoreCase("querycarprice")==0)
	        return 15;
	    else if(argument.compareToIgnoreCase("queryroomprice")==0)
	        return 16;
	    else if(argument.compareToIgnoreCase("reserveflight")==0)
	        return 17;
	    else if(argument.compareToIgnoreCase("reservecar")==0)
	        return 18;
	    else if(argument.compareToIgnoreCase("reserveroom")==0)
	        return 19;
	    else if(argument.compareToIgnoreCase("itinerary")==0)
	        return 20;
	    else if (argument.compareToIgnoreCase("quit")==0)
	        return 21;
	    else if (argument.compareToIgnoreCase("newcustomerid")==0)
	        return 22;
	    else if (argument.compareToIgnoreCase("start")==0)
	    	return 23;
	    else if (argument.compareToIgnoreCase("commit")==0)
	    	return 24;
	    else if (argument.compareToIgnoreCase("abort")==0)
	    	return 25;
	    else if (argument.compareToIgnoreCase("shutdown")==0)
	    	return 26;
	    else
	        return 666;

    }

    public Vector parse(String command)
    {
	    Vector arguments = new Vector();
	    StringTokenizer tokenizer = new StringTokenizer(command,",");
	    String argument ="";
	    while (tokenizer.hasMoreTokens())
	        {
	        argument = tokenizer.nextToken();
	        argument = argument.trim();
	        arguments.add(argument);
	        }
	    return arguments;
    }
    
    //Function to get list of actions for a single transaction [finds HashMap position] 
    public Stack<TransactionObj> getHashPos( int key ){
    	Stack<TransactionObj> st = transHash.get(key);
    	if( st == null ){
    		st = new Stack<TransactionObj>();
    		transHash.put(key, st);
    		st = transHash.get(key);
    	}
    	return st;    	
    }
    
    //abort function
    public void abort( int pTID ){
    	Stack<TransactionObj> st = getHashPos( pTID );
    	while( !st.empty() ){
    		TransactionObj tobjz = st.peek();
    		if( tobjz.type == 0 ){
    			removeData( 1, tobjz.key );
    			writeData( 1, tobjz.key, tobjz.obj );
    		}
    		else
    			removeData( 1, tobjz.key );
    		st.pop();
    	}
    	lm.UnlockAll(pTID);
        transHash.remove(pTID);
        tidBase.remove(pTID);
        String tcpMsg = "abort, 1, " + pTID;
        for( int i = 0; i < 3; i++ ){
        	if( forwardGetBooleanReply( tcpMsg, i, pTID ) )
        		System.out.println("Aborted Transaction at " + RMNames[i] + "RM.");
        	else
        		System.out.println("Failed to abort transaction properly at " + RMNames[i] + "RM.");
        }
    }
 }
