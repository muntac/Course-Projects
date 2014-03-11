import ResInterface.*;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.RMISecurityManager;

import java.util.*;
import java.io.*;
import java.net.*;

    
public class client
{
    static String message = "blank";
    //static Middleware rm = null;
    Socket clientSock;
    ObjectOutputStream output;
	ObjectInputStream input;
	String server = "localhost";
	static int TID = -1; //new variable for Transaction ID
	int port = 3220;
	client(){}
    
	public static void main(String args[]){
        
		client obj = new client();
        BufferedReader stdin = new BufferedReader(new InputStreamReader(System.in));
        String command = "";
        Vector arguments  = new Vector();
        int Id, Cid;
        int flightNum;
        int flightPrice;
        int flightSeats;
        boolean Room;
        boolean Car;
        int price;
        int numRooms;
        int numCars;
        String location;
        
        if( args.length > 0 ) obj.server = args[0];
        if( args.length > 1 ) obj.port = Integer.parseInt( args[1] );
        
        System.out.println("\n\n\tClient Interface");
        System.out.println("Type \"help\" for list of supported commands");
        while(true){
	        System.out.print("\n>");
	        try{
	            //read the next command
	            command =stdin.readLine();
	        }
	        catch (IOException io){
	            System.out.println("Unable to read from standard in");
	            System.exit(1);
	        }
	        //remove heading and trailing white space
	        command=command.trim();
	        arguments=obj.parse(command);
	        
	        //checks if a transaction is going on or not
	        //if not then only accepts "start" or "quit" or "help"
	        int queryType = obj.findChoice((String)arguments.elementAt(0));
	        if( TID == -1 &&  queryType != 23 && queryType != 21 && queryType != 1 ){
	        	System.out.println("Type 'start' to begin a new transaction");
	        	continue;
	        }
	        
	        //decide which of the commands this was
	        switch(obj.findChoice((String)arguments.elementAt(0))){
	        case 1: //help section
	            if(arguments.size()==1)   //command was "help"
	            obj.listCommands();
	            else if (arguments.size()==2)  //command was "help <commandname>"
	            obj.listSpecific((String)arguments.elementAt(1));
	            else  //wrong use of help command
	            System.out.println("Improper use of help command. Type help or help, <commandname>");
	            break;
	            
	        case 2:  //new flight
	            if(arguments.size()!=5){
	            obj.wrongNumber();
	            break;
	            }
	            System.out.println("Adding a new Flight using id: "+arguments.elementAt(1));
	            System.out.println("Flight number: "+arguments.elementAt(2));
	            System.out.println("Add Flight Seats: "+arguments.elementAt(3));
	            System.out.println("Set Flight Price: "+arguments.elementAt(4));
	            
	            try{
	            Id = obj.getInt(arguments.elementAt(1));
	            flightNum = obj.getInt(arguments.elementAt(2));
	            flightSeats = obj.getInt(arguments.elementAt(3));
	            flightPrice = obj.getInt(arguments.elementAt(4));
	            String tcpMsg = "newflight," + Id + "," + flightNum + "," + flightSeats + "," + flightPrice + "," + TID;
	            if( obj.sendRequestBoolean( tcpMsg ) )
	                System.out.println("Flight added");
	            else
	                System.out.println("Flight could not be added");
	            }
	            catch(Exception e){
		            System.out.println("EXCEPTION:");
		            System.out.println(e.getMessage());
		            e.printStackTrace();
	            }
	            break;
	            
	        case 3:  //new Car
	            if(arguments.size()!=5){
	            obj.wrongNumber();
	            break;
	            }
	            System.out.println("Adding a new Car using id: "+arguments.elementAt(1));
	            System.out.println("Car Location: "+arguments.elementAt(2));
	            System.out.println("Add Number of Cars: "+arguments.elementAt(3));
	            System.out.println("Set Price: "+arguments.elementAt(4));
	            try{
	            Id = obj.getInt(arguments.elementAt(1));
	            location = obj.getString(arguments.elementAt(2));
	            numCars = obj.getInt(arguments.elementAt(3));
	            price = obj.getInt(arguments.elementAt(4));
	            String tcpMsg = "newcar," + Id + "," + location + "," + numCars + "," + price + "," + TID;
	            if( obj.sendRequestBoolean( tcpMsg ) )
	                System.out.println("Cars added");
	            else
	                System.out.println("Cars could not be added");
	            }
	            catch(Exception e){
	            System.out.println("EXCEPTION:");
	            System.out.println(e.getMessage());
	            e.printStackTrace();
	            }
	            break;
	            
	        case 4:  //new Room
	            if(arguments.size()!=5){
	            obj.wrongNumber();
	            break;
	            }
	            System.out.println("Adding a new Room using id: "+arguments.elementAt(1));
	            System.out.println("Room Location: "+arguments.elementAt(2));
	            System.out.println("Add Number of Rooms: "+arguments.elementAt(3));
	            System.out.println("Set Price: "+arguments.elementAt(4));
	            try{
	            Id = obj.getInt(arguments.elementAt(1));
	            location = obj.getString(arguments.elementAt(2));
	            numRooms = obj.getInt(arguments.elementAt(3));
	            price = obj.getInt(arguments.elementAt(4));
	            String tcpMsg = "newroom," + Id + "," + location + "," + numRooms + "," + price + "," + TID;
	            if( obj.sendRequestBoolean( tcpMsg ) )
	                System.out.println("Rooms added");
	            else
	                System.out.println("Rooms could not be added");
	            }
	            catch(Exception e){
	            System.out.println("EXCEPTION:");
	            System.out.println(e.getMessage());
	            e.printStackTrace();
	            }
	            break;
	            
	        case 5:  //new Customer
	            if(arguments.size()!=2){
	            obj.wrongNumber();
	            break;
	            }
	            System.out.println("Adding a new Customer using id:"+arguments.elementAt(1));
	            try{
	            	Id = obj.getInt(arguments.elementAt(1));
	            	String tcpMsg = "newCustomer," + Id + "," + TID; 
	            	String customer = obj.sendRequestString( tcpMsg );
	            	if( customer.equals("false") ) System.out.println("Request could not be completed.");
	            	else System.out.println("new customer id:" + Integer.parseInt(customer) );
	            }
	            catch(Exception e){
	            	System.out.println("EXCEPTION:");
	            	System.out.println(e.getMessage());
	            	e.printStackTrace();
	            }
	            break;
	            
	        case 6: //delete Flight
	            if(arguments.size()!=3){
	            obj.wrongNumber();
	            break;
	            }
	            System.out.println("Deleting a flight using id: "+arguments.elementAt(1));
	            System.out.println("Flight Number: "+arguments.elementAt(2));
	            try{
	            Id = obj.getInt(arguments.elementAt(1));
	            flightNum = obj.getInt(arguments.elementAt(2));
	            String tcpMsg = "deleteFlight," + Id + "," + flightNum + "," + TID;
	            if( obj.sendRequestBoolean( tcpMsg ) )
	            	System.out.println("Flight Deleted");
	            else
	                System.out.println("Flight could not be deleted");
	            }
	            catch(Exception e){
	            System.out.println("EXCEPTION:");
	            System.out.println(e.getMessage());
	            e.printStackTrace();
	            }
	            break;
	            
	        case 7: //delete Car
	            if(arguments.size()!=3){
	            obj.wrongNumber();
	            break;
	            }
	            System.out.println("Deleting the cars from a particular location  using id: "+arguments.elementAt(1));
	            System.out.println("Car Location: "+arguments.elementAt(2));
	            try{
	            Id = obj.getInt(arguments.elementAt(1));
	            location = obj.getString(arguments.elementAt(2));
	            String tcpMsg = "deleteCar," + Id + "," + location + "," + TID;
	            if( obj.sendRequestBoolean( tcpMsg ) )
	            	System.out.println("Cars Deleted");
	            else
	                System.out.println("Cars could not be deleted");
	            }
	            catch(Exception e){
	            System.out.println("EXCEPTION:");
	            System.out.println(e.getMessage());
	            e.printStackTrace();
	            }
	            break;
	            
	        case 8: //delete Room
	            if(arguments.size()!=3){
	            obj.wrongNumber();
	            break;
	            }
	            System.out.println("Deleting all rooms from a particular location  using id: "+arguments.elementAt(1));
	            System.out.println("Room Location: "+arguments.elementAt(2));
	            try{
	            Id = obj.getInt(arguments.elementAt(1));
	            location = obj.getString(arguments.elementAt(2));
	            String tcpMsg = "deleteRoom," + Id + "," + location + "," + TID;
	            if( obj.sendRequestBoolean( tcpMsg ) )
	            	System.out.println("Rooms Deleted");
	            else
	                System.out.println("Rooms could not be deleted");
	            }
	            catch(Exception e){
	            System.out.println("EXCEPTION:");
	            System.out.println(e.getMessage());
	            e.printStackTrace();
	            }
	            break;
	            
	        case 9: //delete Customer
	            if(arguments.size()!=3){
	            obj.wrongNumber();
	            break;
	            }
	            System.out.println("Deleting a customer from the database using id: "+arguments.elementAt(1));
	            System.out.println("Customer id: "+arguments.elementAt(2));
	            try{
	            Id = obj.getInt(arguments.elementAt(1));
	            int customer = obj.getInt(arguments.elementAt(2));
	            String tcpMsg = "deleteCustomer," + Id + "," + customer + "," + TID;
	            if( obj.sendRequestBoolean( tcpMsg ) )
	            	System.out.println("Customer Deleted");
	            else
	                System.out.println("Customer could not be deleted");
	            }
	            catch(Exception e){
	            System.out.println("EXCEPTION:");
	            System.out.println(e.getMessage());
	            e.printStackTrace();
	            }
	            break;
	            
	        case 10: //querying a flight
	            if(arguments.size()!=3){
	            obj.wrongNumber();
	            break;
	            }
	            System.out.println("Querying a flight using id: "+arguments.elementAt(1));
	            System.out.println("Flight number: "+arguments.elementAt(2));
	            try{
	            Id = obj.getInt(arguments.elementAt(1));
	            flightNum = obj.getInt(arguments.elementAt(2));
	            String tcpMsg = "queryFlight," + Id + "," + flightNum + "," + TID;
	            String status = obj.sendRequestString( tcpMsg );
		            if( status.equals("false") ) 
		            	System.out.println("Request could not be completed.");
		            else
		            	System.out.println("Number of seats available:"+status);
		        }
	            catch(Exception e){
	            System.out.println("EXCEPTION:");
	            System.out.println(e.getMessage());
	            e.printStackTrace();
	            }
	            break;
	            
	        case 11: //querying a Car Location
	            if(arguments.size()!=3){
	            obj.wrongNumber();
	            break;
	            }
	            System.out.println("Querying a car location using id: "+arguments.elementAt(1));
	            System.out.println("Car location: "+arguments.elementAt(2));
	            try{
	            Id = obj.getInt(arguments.elementAt(1));
	            location = obj.getString(arguments.elementAt(2));
	            String tcpMsg = "queryCar," + Id + "," + location + "," + TID;
	            String status = obj.sendRequestString( tcpMsg );
		            if( status.equals("false") ) 
		            	System.out.println("Request could not be completed.");
		            else
		            	System.out.println("number of Cars at this location:"+status);
		        }
	            catch(Exception e){
	            System.out.println("EXCEPTION:");
	            System.out.println(e.getMessage());
	            e.printStackTrace();
	            }
	            break;
	            
	        case 12: //querying a Room location
	            if(arguments.size()!=3){
	            obj.wrongNumber();
	            break;
	            }
	            System.out.println("Querying a room location using id: "+arguments.elementAt(1));
	            System.out.println("Room location: "+arguments.elementAt(2));
	            try{
	            Id = obj.getInt(arguments.elementAt(1));
	            location = obj.getString(arguments.elementAt(2));
	            String tcpMsg = "queryRoom," + Id + "," + location + "," + TID;
	            String status = obj.sendRequestString( tcpMsg );
		            if( status.equals("false") ) 
		            	System.out.println("Request could not be completed.");
		            else
		            	System.out.println("number of Rooms at this location:"+status);
	            }
	            catch(Exception e){
	            System.out.println("EXCEPTION:");
	            System.out.println(e.getMessage());
	            e.printStackTrace();
	            }
	            break;
	            
	        case 13: //querying Customer Information
	            if(arguments.size()!=3){
	            obj.wrongNumber();
	            break;
	            }
	            System.out.println("Querying Customer information using id: "+arguments.elementAt(1));
	            System.out.println("Customer id: "+arguments.elementAt(2));
	            try{
	            Id = obj.getInt(arguments.elementAt(1));
	            int customer = obj.getInt(arguments.elementAt(2));
	            String tcpMsg = "queryCustomer," + Id + "," + customer + "," + TID;
	            String status = obj.sendRequestString( tcpMsg );
		            if( status.equals("false") ) 
		            	System.out.println("Request could not be completed.");
		            else
		            	System.out.println("Customer info: " + status );
	            }
	            catch(Exception e){
	            System.out.println("EXCEPTION:");
	            System.out.println(e.getMessage());
	            e.printStackTrace();
	            }
	            break;               
	            
	        case 14: //querying a flight Price
	            if(arguments.size()!=3){
	            obj.wrongNumber();
	            break;
	            }
	            System.out.println("Querying a flight Price using id: "+arguments.elementAt(1));
	            System.out.println("Flight number: "+arguments.elementAt(2));
	            try{
	            Id = obj.getInt(arguments.elementAt(1));
	            flightNum = obj.getInt(arguments.elementAt(2));
	            String tcpMsg = "queryFlightPrice," + Id + "," + flightNum + "," + TID;
	            String status = obj.sendRequestString( tcpMsg );
		            if( status.equals("false") ) 
		            	System.out.println("Request could not be completed.");
		            else
		            	System.out.println("Price of a seat:"+status);
	            }
	            catch(Exception e){
	            	System.out.println("EXCEPTION:");
	            	System.out.println(e.getMessage());
	            	e.printStackTrace();
	            }
	            break;
	            
	        case 15: //querying a Car Price
	            if(arguments.size()!=3){
	            	obj.wrongNumber();
	            	break;
	            }
	            System.out.println("Querying a car price using id: "+arguments.elementAt(1));
	            System.out.println("Car location: "+arguments.elementAt(2));
	            try{
	            	Id = obj.getInt(arguments.elementAt(1));
	            	location = obj.getString(arguments.elementAt(2));
	            	String tcpMsg = "queryCarPrice," + Id + "," + location + "," + TID;
	                String status = obj.sendRequestString( tcpMsg );
	    	            if( status.equals("false") ) 
	    	            	System.out.println("Request could not be completed.");
	    	            else
	    	            	System.out.println("Price of a car at this location:"+status);
	            }
	            catch(Exception e){
	            System.out.println("EXCEPTION:");
	            System.out.println(e.getMessage());
	            e.printStackTrace();
	            }                
	            break;
	
	        case 16: //querying a Room price
	            if(arguments.size()!=3){
	            obj.wrongNumber();
	            break;
	            }
	            System.out.println("Querying a room price using id: "+arguments.elementAt(1));
	            System.out.println("Room Location: "+arguments.elementAt(2));
	            try{
	            Id = obj.getInt(arguments.elementAt(1));
	            location = obj.getString(arguments.elementAt(2));
	            String tcpMsg = "queryRoomPrice," + Id + "," + location + "," + TID;
	            String status = obj.sendRequestString( tcpMsg );
		            if( status.equals("false") ) 
		            	System.out.println("Request could not be completed.");
		            else
		            	System.out.println("Price of Rooms at this location:"+status);
	            }
	            catch(Exception e){
	            System.out.println("EXCEPTION:");
	            System.out.println(e.getMessage());
	            e.printStackTrace();
	            }
	            break;
	            
	        case 17:  //reserve a flight
	            if(arguments.size()!=4){
	            obj.wrongNumber();
	            break;
	            }
	            System.out.println("Reserving a seat on a flight using id: "+arguments.elementAt(1));
	            System.out.println("Customer id: "+arguments.elementAt(2));
	            System.out.println("Flight number: "+arguments.elementAt(3));
	            try{
	            Id = obj.getInt(arguments.elementAt(1));
	            int customer = obj.getInt(arguments.elementAt(2));
	            flightNum = obj.getInt(arguments.elementAt(3));
	            String tcpMsg = "reserveFlight," + Id + "," + customer + "," + flightNum + "," + TID;
	            if( obj.sendRequestBoolean(tcpMsg) )
	                System.out.println("Flight Reserved");
	            else
	                System.out.println("Flight could not be reserved.");
	            }
	            catch(Exception e){
	            System.out.println("EXCEPTION:");
	            System.out.println(e.getMessage());
	            e.printStackTrace();
	            }
	            break;
	            
	        case 18:  //reserve a car
	            if(arguments.size()!=4){
	            obj.wrongNumber();
	            break;
	            }
	            System.out.println("Reserving a car at a location using id: "+arguments.elementAt(1));
	            System.out.println("Customer id: "+arguments.elementAt(2));
	            System.out.println("Location: "+arguments.elementAt(3));
	            
	            try{
	            Id = obj.getInt(arguments.elementAt(1));
	            int customer = obj.getInt(arguments.elementAt(2));
	            location = obj.getString(arguments.elementAt(3));
	            String tcpMsg = "reserveCar," + Id + "," + customer + "," + location + "," + TID;
	            if( obj.sendRequestBoolean(tcpMsg) )
	            	System.out.println("Car Reserved");
	            else
	                System.out.println("Car could not be reserved.");
	            }
	            catch(Exception e){
	            System.out.println("EXCEPTION:");
	            System.out.println(e.getMessage());
	            e.printStackTrace();
	            }
	            break;
	            
	        case 19:  //reserve a room
	            if(arguments.size()!=4){
	            obj.wrongNumber();
	            break;
	            }
	            System.out.println("Reserving a room at a location using id: "+arguments.elementAt(1));
	            System.out.println("Customer id: "+arguments.elementAt(2));
	            System.out.println("Location: "+arguments.elementAt(3));
	            try{
	            Id = obj.getInt(arguments.elementAt(1));
	            int customer = obj.getInt(arguments.elementAt(2));
	            location = obj.getString(arguments.elementAt(3));
	            String tcpMsg = "reserveRoom," + Id + "," + customer + "," + location + "," + TID;
	            if( obj.sendRequestBoolean(tcpMsg) )
	            	System.out.println("Room Reserved");
	            else
	                System.out.println("Room could not be reserved.");
	            }
	            catch(Exception e){
	            System.out.println("EXCEPTION:");
	            System.out.println(e.getMessage());
	            e.printStackTrace();
	            }
	            break;
	            
	        case 20:  //reserve an Itinerary
	            /*if(arguments.size()<7){
	            obj.wrongNumber();
	            break;
	            }*/
	            System.out.println("Reserving an Itinerary using id:"+arguments.elementAt(1));
	            System.out.println("Customer id:"+arguments.elementAt(2));
	            for(int i=0;i<arguments.size()-6;i++)
	            System.out.println("Flight number"+arguments.elementAt(3+i));
	            System.out.println("Location for Car/Room booking:"+arguments.elementAt(arguments.size()-3));
	            System.out.println("Car to book?:"+arguments.elementAt(arguments.size()-2));
	            System.out.println("Room to book?:"+arguments.elementAt(arguments.size()-1));
	            try{
	            Id = obj.getInt(arguments.elementAt(1));
	            Cid = obj.getInt(arguments.elementAt(2));
	            String tcpMsg = "itinerary," + Id + "," + Cid;
	            Vector flightNumbers = new Vector();
	            for(int i=0;i<arguments.size()-6;i++)
	                tcpMsg = tcpMsg + "," + arguments.elementAt(3+i); 
	            location = obj.getString(arguments.elementAt(arguments.size()-3));
	            tcpMsg = tcpMsg + "," + location;
	            int Ccar = obj.getInt(arguments.elementAt(arguments.size()-2));
	            tcpMsg = tcpMsg + "," + Ccar;
	            int Rroom = obj.getInt(arguments.elementAt(arguments.size()-1));
	            tcpMsg = tcpMsg + "," + Rroom;
	            tcpMsg = tcpMsg + "," + TID;
	            if( obj.sendRequestBoolean( tcpMsg ) )
	            	System.out.println("Itinerary Reserved");
	            else
	                System.out.println("Not all items in itinerary could be reserved.");
	            }
	            catch(Exception e){
	            System.out.println("EXCEPTION:");
	            System.out.println(e.getMessage());
	            e.printStackTrace();
	            }
	            break;
	                        
	        case 21:  //quit the client
	            if(arguments.size()!=1){
	            obj.wrongNumber();
	            break;
	            }
	            System.out.println("Quitting client.");
	            System.exit(1);
	            
	            
	        case 22:  //new Customer given id
	            if(arguments.size()!=3){
		            obj.wrongNumber();
		            break;
		        }
	            System.out.println("Adding a new Customer using id:"+arguments.elementAt(1) + " and cid " +arguments.elementAt(2));
		            try{
		            	Id = obj.getInt(arguments.elementAt(1));
		            	Cid = obj.getInt(arguments.elementAt(2));
		            	String tcpMsg = "newcustomerid," + Id + "," + Cid + "," + TID; 
		            	if( obj.sendRequestBoolean( tcpMsg ) ) System.out.println("new customer id:" + Cid );
		            	else System.out.println("This ID is already in use.");
		            }
		            catch(Exception e){
		            	System.out.println("EXCEPTION:");
		            	System.out.println(e.getMessage());
		            	e.printStackTrace();
		            }	        	
	            break;
	        
	        case 23:  //start a new transaction
	        	if(arguments.size()!=1){
		            obj.wrongNumber();
		            break;
		        }
	        	if( TID != -1 ){
	        		System.out.println("Commit or Abort current transaction first.");
	        		break;
	        	}
	        	//send string "start" to Middleware
	        	System.out.println("Starting a new transaction");
	            try{
	            	String tcpMsg = "start"; 
	            	String status = obj.sendRequestString( tcpMsg );
		            if( status.equals("false") ) 
		            	System.out.println("Request could not be completed.");
		            else{
		            	TID = Integer.parseInt(status);
		            	System.out.println("New transaction started. Please complete full transaction within 30 seconds.");
		            	System.out.println(TID);
		            	//implement timeout
		            }
	            }
	            catch(Exception e){
	            	System.out.println("EXCEPTION:");
	            	System.out.println(e.getMessage());
	            	e.printStackTrace();
	            }
            break;
	        
	        case 24: //commits transaction
	        	if(arguments.size()!=3){
		            obj.wrongNumber();
		            break;
		        }
	        	//send Commit command to Middleware with Transaction ID
	        	System.out.println("Committing current transaction");
	            try{
	            	Id = obj.getInt(arguments.elementAt(1));
		        	String crashCon = obj.getString(arguments.elementAt(2));
		        	String tcpMsg = "commit, 1," + TID + "," + crashCon; 
	            	if( obj.sendRequestBoolean( tcpMsg ) ){
	            		System.out.println("Transaction successfully commited.");
	            		TID = -1;
	            	}
		            else{
		            	System.out.println("Commit failed.");
		            	TID = -1;
		            }
	            }
	            catch(Exception e){
	            	System.out.println("EXCEPTION:");
	            	System.out.println(e.getMessage());
	            	e.printStackTrace();
	            }
	            break;
	            
	        case 25: //aborts transaction
	        	if(arguments.size()!=1){
		            obj.wrongNumber();
		            break;
		        }
	        	//send Abort command to Middleware with Transaction ID
	        	System.out.println("Aborting current transaction");
	            try{
	            	String tcpMsg = "abort,1," + TID; 
	            	if( obj.sendRequestBoolean( tcpMsg ) ){
	            		System.out.println("Transaction successfully aborted.");
	            		TID = -1;
	            	}
		            else{
		            	System.out.println("Abort failed.");
		            	TID = -1;//Should never enter this area if all RMs are up and working
		            }
	            }
	            catch(Exception e){
	            	System.out.println("EXCEPTION:");
	            	System.out.println(e.getMessage());
	            	e.printStackTrace();
	            }
	            break;
	            
	        case 26: //aborts transaction
	        	if(arguments.size()!=1){
		            obj.wrongNumber();
		            break;
		        }
	        	//send Abort command to Middleware with Transaction ID
	        	System.out.println("Aborting current transaction");
	            try{
	            	String tcpMsg = "abort,1," + TID;
	            	if( TID == -1 )
	            		System.out.println("Client successfully shutdown.");
	            	else if(obj.sendRequestBoolean( tcpMsg ) ){
	            		System.out.println("Client successfully shutdown.");
	            		TID = -1;
	            	}
		            else{
		            	System.out.println("Shutdown failed.");
		            	TID = -1;//Should never enter this area if all RMs are up and working
		            }
	            }
	            catch(Exception e){
	            	System.out.println("EXCEPTION:");
	            	System.out.println(e.getMessage());
	            	e.printStackTrace();
	            }
	            System.exit(1);
	            break;
	        	
	        default:
	            System.out.println("The interface does not support this command.");
	            break;
	        }//end of switch
        }//end of while true
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

    public void listCommands()
    {
    System.out.println("\nWelcome to the client interface provided to test your project.");
    System.out.println("Commands accepted by the interface are:");
    System.out.println("help");
    System.out.println("newflight\nnewcar\nnewroom\nnewcustomer\nnewcusomterid\ndeleteflight\ndeletecar\ndeleteroom");
    System.out.println("deletecustomer\nqueryflight\nquerycar\nqueryroom\nquerycustomer");
    System.out.println("queryflightprice\nquerycarprice\nqueryroomprice");
    System.out.println("reserveflight\nreservecar\nreserveroom\nitinerary\nstart\ncommit\nabort");
    System.out.println("nquit");
    System.out.println("\ntype help, <commandname> for detailed info(NOTE the use of comma).");
    }


    public void listSpecific(String command)
    {
    System.out.print("Help on: ");
    switch(findChoice(command))
        {
        case 1:
        System.out.println("Help");
        System.out.println("\nTyping help on the prompt gives a list of all the commands available.");
        System.out.println("Typing help, <commandname> gives details on how to use the particular command.");
        break;

        case 2:  //new flight
        System.out.println("Adding a new Flight.");
        System.out.println("Purpose:");
        System.out.println("\tAdd information about a new flight.");
        System.out.println("\nUsage:");
        System.out.println("\tnewflight,<id>,<flightnumber>,<flightSeats>,<flightprice>");
        break;
        
        case 3:  //new Car
        System.out.println("Adding a new Car.");
        System.out.println("Purpose:");
        System.out.println("\tAdd information about a new car location.");
        System.out.println("\nUsage:");
        System.out.println("\tnewcar,<id>,<location>,<numberofcars>,<pricepercar>");
        break;
        
        case 4:  //new Room
        System.out.println("Adding a new Room.");
        System.out.println("Purpose:");
        System.out.println("\tAdd information about a new room location.");
        System.out.println("\nUsage:");
        System.out.println("\tnewroom,<id>,<location>,<numberofrooms>,<priceperroom>");
        break;
        
        case 5:  //new Customer
        System.out.println("Adding a new Customer.");
        System.out.println("Purpose:");
        System.out.println("\tGet the system to provide a new customer id. (same as adding a new customer)");
        System.out.println("\nUsage:");
        System.out.println("\tnewcustomer,<id>");
        break;
        
        
        case 6: //delete Flight
        System.out.println("Deleting a flight");
        System.out.println("Purpose:");
        System.out.println("\tDelete a flight's information.");
        System.out.println("\nUsage:");
        System.out.println("\tdeleteflight,<id>,<flightnumber>");
        break;
        
        case 7: //delete Car
        System.out.println("Deleting a Car");
        System.out.println("Purpose:");
        System.out.println("\tDelete all cars from a location.");
        System.out.println("\nUsage:");
        System.out.println("\tdeletecar,<id>,<location>,<numCars>");
        break;
        
        case 8: //delete Room
        System.out.println("Deleting a Room");
        System.out.println("\nPurpose:");
        System.out.println("\tDelete all rooms from a location.");
        System.out.println("Usage:");
        System.out.println("\tdeleteroom,<id>,<location>,<numRooms>");
        break;
        
        case 9: //delete Customer
        System.out.println("Deleting a Customer");
        System.out.println("Purpose:");
        System.out.println("\tRemove a customer from the database.");
        System.out.println("\nUsage:");
        System.out.println("\tdeletecustomer,<id>,<customerid>");
        break;
        
        case 10: //querying a flight
        System.out.println("Querying flight.");
        System.out.println("Purpose:");
        System.out.println("\tObtain Seat information about a certain flight.");
        System.out.println("\nUsage:");
        System.out.println("\tqueryflight,<id>,<flightnumber>");
        break;
        
        case 11: //querying a Car Location
        System.out.println("Querying a Car location.");
        System.out.println("Purpose:");
        System.out.println("\tObtain number of cars at a certain car location.");
        System.out.println("\nUsage:");
        System.out.println("\tquerycar,<id>,<location>");        
        break;
        
        case 12: //querying a Room location
        System.out.println("Querying a Room Location.");
        System.out.println("Purpose:");
        System.out.println("\tObtain number of rooms at a certain room location.");
        System.out.println("\nUsage:");
        System.out.println("\tqueryroom,<id>,<location>");        
        break;
        
        case 13: //querying Customer Information
        System.out.println("Querying Customer Information.");
        System.out.println("Purpose:");
        System.out.println("\tObtain information about a customer.");
        System.out.println("\nUsage:");
        System.out.println("\tquerycustomer,<id>,<customerid>");
        break;               
        
        case 14: //querying a flight for price 
        System.out.println("Querying flight.");
        System.out.println("Purpose:");
        System.out.println("\tObtain price information about a certain flight.");
        System.out.println("\nUsage:");
        System.out.println("\tqueryflightprice,<id>,<flightnumber>");
        break;
        
        case 15: //querying a Car Location for price
        System.out.println("Querying a Car location.");
        System.out.println("Purpose:");
        System.out.println("\tObtain price information about a certain car location.");
        System.out.println("\nUsage:");
        System.out.println("\tquerycarprice,<id>,<location>");        
        break;
        
        case 16: //querying a Room location for price
        System.out.println("Querying a Room Location.");
        System.out.println("Purpose:");
        System.out.println("\tObtain price information about a certain room location.");
        System.out.println("\nUsage:");
        System.out.println("\tqueryroomprice,<id>,<location>");        
        break;

        case 17:  //reserve a flight
        System.out.println("Reserving a flight.");
        System.out.println("Purpose:");
        System.out.println("\tReserve a flight for a customer.");
        System.out.println("\nUsage:");
        System.out.println("\treserveflight,<id>,<customerid>,<flightnumber>");
        break;
        
        case 18:  //reserve a car
        System.out.println("Reserving a Car.");
        System.out.println("Purpose:");
        System.out.println("\tReserve a given number of cars for a customer at a particular location.");
        System.out.println("\nUsage:");
        System.out.println("\treservecar,<id>,<customerid>,<location>,<nummberofCars>");
        break;
        
        case 19:  //reserve a room
        System.out.println("Reserving a Room.");
        System.out.println("Purpose:");
        System.out.println("\tReserve a given number of rooms for a customer at a particular location.");
        System.out.println("\nUsage:");
        System.out.println("\treserveroom,<id>,<customerid>,<location>,<nummberofRooms>");
        break;
        
        case 20:  //reserve an Itinerary
        System.out.println("Reserving an Itinerary.");
        System.out.println("Purpose:");
        System.out.println("\tBook one or more flights.Also book zero or more cars/rooms at a location.");
        System.out.println("\nUsage:");
        System.out.println("\titinerary,<id>,<customerid>,<flightnumber1>....<flightnumberN>,<LocationToBookCarsOrRooms>,<NumberOfCars>,<NumberOfRoom>");
        break;
        

        case 21:  //quit the client
        System.out.println("Quitting client.");
        System.out.println("Purpose:");
        System.out.println("\tExit the client application.");
        System.out.println("\nUsage:");
        System.out.println("\tquit");
        break;
        
        case 22:  //new customer with id
            System.out.println("Create new customer providing an id");
            System.out.println("Purpose:");
            System.out.println("\tCreates a new customer with the id provided");
            System.out.println("\nUsage:");
            System.out.println("\tnewcustomerid, <id>, <customerid>");
            break;
            
        case 23:  //starts a new transaction
            System.out.println("Start a new transaction");
            System.out.println("Purpose:");
            System.out.println("\tBegins a new transaction");
            System.out.println("\nUsage:");
            System.out.println("\tstart");
            break;
        
        case 24:  //commits transaction
            System.out.println("Commit current transaction");
            System.out.println("Purpose:");
            System.out.println("\tSave changes made by current transaction to disk");
            System.out.println("        Also closes the current transaction.");
            System.out.println("\nUsage:");
            System.out.println("\tcommit");
            break;
            
        case 25:  //abort transaction
            System.out.println("Abort current transaction");
            System.out.println("Purpose:");
            System.out.println("\tTo cancel current transaction");
            System.out.println("        Also closes the current transaction.");
            System.out.println("\nUsage:");
            System.out.println("\tabort");
            break;

        default:
        System.out.println(command);
        System.out.println("The interface does not support this command.");
        break;
        }
    }
    
    public void wrongNumber() {
    System.out.println("The number of arguments provided in this command are wrong.");
    System.out.println("Type help, <commandname> to check usage of this command.");
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
    
    private boolean sendRequestBoolean( String msgtcp ){//For queries that only require boolean replies
		String messageIn = ""; 
		try{
        	clientSock = new Socket( this.server, port );
        	output = new ObjectOutputStream(clientSock.getOutputStream());
			output.flush();
			input = new ObjectInputStream(clientSock.getInputStream());
			messageIn = (String)input.readObject();
			if( messageIn.equals("Connection Successful")){
				output.writeObject( msgtcp );//this sends the msg
				output.flush();
				messageIn = (String)input.readObject();
			}
			else messageIn = "false";
		} 
        catch (Exception e){    
            System.err.println("Client exception: " + e.toString());
            e.printStackTrace();
        }
		finally{
			//4. Closing connection
			try{
				//Close streams
				input.close();
				output.close();
				//Close socket
				clientSock.close();
			}
			catch(Exception e){
				System.err.println(e);
			}
		}
		if( messageIn.equals("true") ) return true;
		else if( messageIn.equals("timedout") ){
			System.out.println("ERROR: The transaction has been cancelled either due to CLIENT INACTIVITY or SERVER FAILURE.");
			TID = -1;
			return false;
		}
		else return false;
	}
    
    private String sendRequestString( String msgtcp ){//For queries that require string replies
		String messageIn = ""; 
		try{
        	clientSock = new Socket( this.server, port );
        	output = new ObjectOutputStream(clientSock.getOutputStream());
			output.flush();
			input = new ObjectInputStream(clientSock.getInputStream());
			messageIn = (String)input.readObject();
			if( messageIn.equals("Connection Successful")){
				output.writeObject( msgtcp );//this sends the msg
				output.flush();
				messageIn = (String)input.readObject();
				//parse msg
				String delim = "[,]+";//using + allows to treat consecutive ,s as one, so no empty strings
				String[] result = messageIn.split(delim);
				//check header for true or false
				if( result[0].equals("true") ){
					messageIn = result[1];
					//for( int i = 2; i < result.length; i++ ) messageIn = messageIn + " " + result[i];
				}
				else if( result[0].equals("timedout") ){
					TID = -1;
					System.out.println("ERROR: The transaction has been cancelled either due to CLIENT INACTIVITY or SERVER FAILURE.");
					messageIn = "false";
				}
				else messageIn = "false";
			}
			else messageIn = "false";
		} 
        catch (Exception e){    
            System.err.println("Client exception: " + e.toString());
            e.printStackTrace();
       }
		finally{
			//4. Closing connection
			try{
				//Close streams
				input.close();
				output.close();
				//Close socket
				clientSock.close();
			}
			catch(Exception e){
				System.err.println(e);
			}
		}
	   return messageIn;
	}
}