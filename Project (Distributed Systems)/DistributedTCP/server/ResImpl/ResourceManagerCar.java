// -------------------------------
// adapted from Kevin T. Manley
// modified by Muntasir M. Chowdhury
// CSE 593

package ResImpl;

import LockManager.DeadlockException;
import LockManager.LockManager;
import ResImpl.MiddlewareImpl.timeout;
import ResInterface.*;

import java.util.*;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.rmi.registry.Registry;
import java.rmi.registry.LocateRegistry;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.rmi.RMISecurityManager;
import java.io.*;
import java.net.*;
public class ResourceManagerCar{
	ServerSocket serverSock;
	int port = 3222;
	LockManager lm = new LockManager ();//only one version for the whole RM
	HashMap<Integer, Stack<TransactionObj>> transHash = new HashMap();
	HashMap<Integer, Integer> tidBase = new HashMap();
	public ResourceManagerCar(){
		timeout tme = new timeout();
		tme.start();
		try{
        	serverSock = new ServerSocket(port);
        }
        catch(Exception e){
        	e.printStackTrace();
        }
		System.out.println("RMCar Online.");
		while(true){
			try{
				Socket connection = serverSock.accept();
				ResourceManagerCar2 rmcd = new ResourceManagerCar2(connection, lm, transHash, tidBase);
				rmcd.start();
			}
			catch(Exception e){
				e.printStackTrace();			
			}
		
		}
		/*try{
			serverSock.close();
		}
		catch(Exception e){
			e.printStackTrace();
		}*/
	}
	public static void main(String args[]) {
                
        ResourceManagerCar rmc = new ResourceManagerCar();
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
        				  ResourceManagerCar2 mwd = new ResourceManagerCar2(connection, lm, transHash, tidBase);
        				  mwd.abort(TID);
				          System.out.println("Transaction " + TID + " timed out.");
				          tidBase.remove(TID);
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

class ResourceManagerCar2 extends Thread implements ResourceManager 
{
    static protected RMHashtable m_itemHT = new RMHashtable();
    //Variables related to connection to Middleware [RM works as server]
    ObjectOutputStream outputMW;
	ObjectInputStream inputMW;
	Socket connection = null;
	LockManager lm;//used for lock management
	HashMap<Integer, Stack<TransactionObj>> transHash = new HashMap(); //used for transaction management
	HashMap<Integer, Integer> tidBase = new HashMap();
	
	public ResourceManagerCar2( Socket s, LockManager lmm, HashMap<Integer, Stack<TransactionObj>> transHashH, HashMap<Integer, Integer> tidBaseB ){
		connection = s;
		this.lm = lmm;
		this.transHash = transHashH;
		this.tidBase = tidBaseB;
	}
	
	public void run(){
    	//Act as server for clients
		try{
	    				
			// Get Input and Output Streams
			outputMW = new ObjectOutputStream(connection.getOutputStream());
			outputMW.flush();
			inputMW = new ObjectInputStream(connection.getInputStream());
			sendMessage("Connection Successful");
			
			// Communication with MW
			try{
				String msgReq = (String)inputMW.readObject(); //read msg from MW
				System.out.println("MW>" + msgReq); //to keep track of who's saying what
				//Parse message and act accordingly
				Vector arguments = new Vector();
				arguments = parse( msgReq );
				
				//decide which of the commands this was
				int flightNum, flightPrice, flightSeats, Id, Cid, numCars, numRooms, price, TID;
				String location;
		        switch(findChoice((String)arguments.elementAt(0))){
			        case 1: //help section
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
			            	System.out.println("TRANSACTION not found.");
			            	sendMessage("timedout");
			            	break;
			            }
			            if( addCars( Id, location, numCars, price, TID ) )
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
			            	System.out.println("TRANSACTION not found.");
			            	sendMessage("timedout");
			            	break;
			            }
			            if( deleteCars( Id, location, TID ) )
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
			            
			        case 11: //querying a Car Location
			            System.out.println("Querying a car location using id: "+arguments.elementAt(1));
			            System.out.println("Car location: "+arguments.elementAt(2));
			            try{
			            Id = getInt(arguments.elementAt(1));
			            location = getString(arguments.elementAt(2));
			            TID = getInt(arguments.elementAt(3));
			            if(tidBase.get(TID) == null){
			            	System.out.println("TRANSACTION not found.");
			            	sendMessage("timedout");
			            	break;
			            }
			            int x = queryCars( Id, location, TID );
			            String status;
			            if( x == -1 )
			            	status = "false";
			            else
			            	status = "true," + x;
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
				            	System.out.println("TRANSACTION not found.");
				            	sendMessage("timedout");
				            	break;
				            }
			            	int x = queryCarsPrice( Id, location, TID );
			            	String status;
			            	if( x == -1 )
			            	   status = "false";
			            	else
			            	   status = "true," + x;
			            	//System.out.println("RM>What's going on");
			            	sendMessage( status );
			            }
			            catch(Exception e){
			            System.out.println("EXCEPTION:");
			            System.out.println(e.getMessage());
			            e.printStackTrace();
			            sendMessage("false");
			            }               
			            break;
			
			        case 18:  //reserve a car
			            /*if(arguments.size()!=4){
			            
			            break;
			            }*/
			            System.out.println("Reserving a car at a location using id: "+arguments.elementAt(1));
			            System.out.println("Customer id: "+arguments.elementAt(2));
			            System.out.println("Location: "+arguments.elementAt(3));
			            System.out.println("Transaction ID: "+arguments.elementAt(4));
			            try{
				            Id = getInt(arguments.elementAt(1));
				            int customer = getInt(arguments.elementAt(2));
				            location = getString(arguments.elementAt(3));
				            TID = getInt(arguments.elementAt(4));
				            if(tidBase.get(TID) == null){
				            	System.out.println("TRANSACTION not found.");
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
			            
			        case 21:  //quit the client
			            System.out.println("Quitting client.");
			            System.exit(1);
			            break;
			        case 23: //inserts a new transactionID
			        	System.out.println("RECORDING New Transaction ID " + ".");
			        	TID = getInt(arguments.elementAt(1));
			        	if(tidBase.get(TID) != null){
			            	System.out.println("TRANSACTION ALREADY EXISTS. Cannot insert as new.");
			            	abort(TID);
			            	sendMessage("timedout");
			            	break;
			            }
			        	tidBase.put( TID, 120000 );
			        	sendMessage("true");
			        	break;
			        case 24: //commit
			        	Id = getInt(arguments.elementAt(1));
			            TID = getInt(arguments.elementAt(2));
			            if(tidBase.get(TID) == null){
			            	System.out.println("TRANSACTION not found.");
			            	sendMessage("timedout");
			            	break;
			            }
			            lm.UnlockAll(TID);
			            transHash.remove(TID);
			            System.out.println("Transaction Committed");
			            sendMessage("true");
			        	break;
			        	
			        case 25: //abort
			        	Id = getInt(arguments.elementAt(1));
			            TID = getInt(arguments.elementAt(2));
			            if(tidBase.get(TID) == null){
			            	System.out.println("TRANSACTION not found.");
			            	sendMessage("false");
			            }
			            else{
			            	System.out.println("Transaction Aborted");
				            sendMessage("true");
			            }
			            abort(TID);
			            break;
			        case 27://deleteRes
			        	try{
				            Id = getInt(arguments.elementAt(1));
				            location = getString(arguments.elementAt(2));
				            int cnt = getInt(arguments.elementAt(3));
				            TID = getInt(arguments.elementAt(4));
				            if(tidBase.get(TID) == null){
				            	System.out.println("TRANSACTION not found.");
				            	sendMessage("timedout");
				            	break;
				            }
				            if( deleteRes( Id, location, cnt, TID ) )
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
			        case 28: //Vote Request
			        	try{
				            Id = getInt(arguments.elementAt(1));
				            TID = getInt(arguments.elementAt(2));
				            if(tidBase.get(TID) == null){
				            	System.out.println("TRANSACTION not found.");
				            	sendMessage("timedout");
				            	break;
				            }
				            System.out.println("Voting YES to Commit Request.");
				            sendMessage("true");
				            //VOTED YES. Therefore, waiting indefinitely. (In this case 10 mins)
				            tidBase.put(TID, 6000000);
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
				inputMW.close();
				outputMW.close();
				//Close socket
				//serverSock.close();
			}
			catch(Exception e){
				System.err.println(e);
			}
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
    protected boolean deleteItem(int id, String key, int pTID)
    {
        Trace.info("RM::deleteItem(" + id + ", " + key + ") called" );
        ReservableItem curObj = (ReservableItem) readData( id, key );
        // Check if there is such an item in the storage
        if ( curObj == null ) {
            Trace.warn("RM::deleteItem(" + id + ", " + key + ") failed--item doesn't exist" );
            return false;
        } else {
        	if (curObj.getReserved()==0) {
            	try{
    	            if( lm.Lock( pTID, curObj.getKey(), 1 ) ){
    	            	//Abort: writeData with curObj.getKey() and curObj
    	            	Stack<TransactionObj> stack = getHashPos( pTID );
    	            	TransactionObj tobjz = new TransactionObj( 0, curObj.getKey(), curObj);
    	            	stack.push( tobjz );
    	            	///////
		    		    removeData(id, curObj.getKey());
		                Trace.info("RM::deleteItem(" + id + ", " + key + ") item deleted" );
		                return true;
    	            }
            	}
            	catch(DeadlockException d){
            		d.printStackTrace();
            		return false;
            	}
            	catch(Exception e){
            		e.printStackTrace();
            		return false;
            	}
            }
            else {
                Trace.info("RM::deleteItem(" + id + ", " + key + ") item can't be deleted because some customers reserved it" );
                return false;
            }
        }
        return false;
    }
    

    // query the number of available seats/rooms/cars
    protected int queryNum(int id, String key, int pTID) {
        Trace.info("RM::queryNum(" + id + ", " + key + ") called" );
        ReservableItem curObj = (ReservableItem) readData( id, key);
        int value = -1;  
        if ( curObj != null ){
        	try{
        		if( lm.Lock( pTID, curObj.getKey(), 0 ) ){
        			value = curObj.getCount();
        		}
        	}
        	catch( DeadlockException d ){
        		d.printStackTrace();
        		return -1;        		
        	}
            catch( Exception e ){
            	e.printStackTrace();
            	return -1;
            }
        }
        Trace.info("RM::queryNum(" + id + ", " + key + ") returns count=" + value);
        return value;
    }
    
    // query the price of an item
    protected int queryPrice(int id, String key, int pTID) {
        Trace.info("RM::queryCarsPrice(" + id + ", " + key + ") called" );
        ReservableItem curObj = (ReservableItem) readData( id, key);
        int value = -1; 
        if ( curObj != null ){
        	try{
        		if( lm.Lock( pTID, curObj.getKey(), 0 ) ){
        			value = curObj.getPrice();
        		}
        	}
        	catch( DeadlockException d ){
        		d.printStackTrace();
        		return -1;        		
        	}
            catch( Exception e ){
            	e.printStackTrace();
            	return -1;
            }
        }
        Trace.info("RM::queryCarsPrice(" + id + ", " + key + ") returns cost=$" + value );
        return value;        
    }
    
    // reserve an item
    protected boolean reserveItem(int id, int customerID, String key, String location, int pTID) {
        Trace.info("RM::reserveItem( " + id + ", customer=" + customerID + ", " +key+ ", "+location+","+pTID+" ) called" );        
       
        // check if the item is available
        ReservableItem item = (ReservableItem)readData(id, key);
        //synchronized( item ){//locking on reserveableItem object
        if ( item == null ) {
            Trace.warn("RM::reserveItem( " + id + ", " + customerID + ", " + key+", " +location+") failed--item doesn't exist" );
            return false;
        } else if (item.getCount()==0) {
            Trace.warn("RM::reserveItem( " + id + ", " + customerID + ", " + key+", " + location+") failed--No more items" );
            return false;
        } else {            
            try{
        		if( lm.Lock( pTID, key, 1 ) ){
        			//Abort: writeData item.getKey() and item [Overwrite]
                	Stack<TransactionObj> stack = getHashPos( pTID );
                	TransactionObj tobjz = new TransactionObj( 0, item.getKey(), item);
                	stack.push( tobjz );
                	///////
                	Car item2 = (Car)readData(id, key);
                	String s = item2.getKey();
                	Car item3 = new Car(s.substring(4), item2.getCount(), item2.getPrice());
                	item3.setCount(item3.getCount() - 1);
                    item3.setReserved(item3.getReserved()+1);
                    writeData( id, item3.getKey(), item3 );
                    //all this mess so as to avoid sending a reference
                    Trace.info("RM::reserveItem( " + id + ", " + customerID + ", " + key + ", " +location+") succeeded" );
                    return true;
        		}
        		else
        			return false;
        	}
        	catch (DeadlockException deadlock) {
                deadlock.printStackTrace();
                return false;
            }
        	catch(Exception e){
        		e.printStackTrace();
        	}
        }
        //}
        return false;
    }
    
    // Create a new flight, or add seats to existing flight
    //  NOTE: if flightPrice <= 0 and the flight already exists, it maintains its current price
    public boolean addFlight(int id, int flightNum, int flightSeats, int flightPrice, int TID)
        throws RemoteException
    {
        Trace.info("RM::addFlight(" + id + ", " + flightNum + ", $" + flightPrice + ", " + flightSeats + ") called" );
        Flight curObj = (Flight) readData( id, Flight.getKey(flightNum) );
        if ( curObj == null ) {
            // doesn't exist...add it
            Flight newObj = new Flight( flightNum, flightSeats, flightPrice );
            writeData( id, newObj.getKey(), newObj );
            Trace.info("RM::addFlight(" + id + ") created new flight " + flightNum + ", seats=" +
                    flightSeats + ", price=$" + flightPrice );
        } else {
            // add seats to existing flight and update the price...
            curObj.setCount( curObj.getCount() + flightSeats );
            if ( flightPrice > 0 ) {
                curObj.setPrice( flightPrice );
            } // if
            writeData( id, curObj.getKey(), curObj );
            Trace.info("RM::addFlight(" + id + ") modified existing flight " + flightNum + ", seats=" + curObj.getCount() + ", price=$" + flightPrice );
        } // else
        return(true);
    }


    
    public boolean deleteFlight(int id, int flightNum, int pTID)
        throws RemoteException
    {
        return deleteItem(id, Flight.getKey(flightNum), pTID);
    }



    // Create a new room location or add rooms to an existing location
    //  NOTE: if price <= 0 and the room location already exists, it maintains its current price
    public boolean addRooms(int id, String location, int count, int price, int TID)
        throws RemoteException
    {
        return false;
    }

    // Delete rooms from a location
    public boolean deleteRooms(int id, String location, int TID)
        throws RemoteException
    {
        return false;//deleteItem(id, Hotel.getKey(location));
        
    }

    // Create a new car location or add cars to an existing location
    //  NOTE: if price <= 0 and the location already exists, it maintains its current price
    public boolean addCars(int id, String location, int count, int price, int pTID)
        throws RemoteException
    {
        Trace.info("RM::addCars(" + id + ", " + location + ", " + count + ", $" + price + ") called" );
        Car curObj = (Car) readData( id, Car.getKey(location) );
        if ( curObj == null ) {
            // car location doesn't exist...add it
            Car newObj = new Car( location, count, price );
            try{
	            if( lm.Lock( pTID, newObj.getKey(), 1 ) ){
	            	//Abort: do a removeData with newObj.getKey and newObj
	            	Stack<TransactionObj> stack = getHashPos( pTID );
	            	TransactionObj tobjz = new TransactionObj( 1, newObj.getKey(), newObj);
	            	stack.push( tobjz );
	            	///////
	            	writeData( id, newObj.getKey(), newObj );
	                Trace.info("RM::addCars(" + id + ") created new location " + location + ", count=" + count + ", price=$" + price );
	            }
            }
            catch(Exception e){
            	e.printStackTrace();
            }
            
        } 
        else {
            // add count to existing car location and update price...
        	try{
	            if( lm.Lock( pTID, curObj.getKey(), 1 ) ){
	            	//Abort: do a writeData with curObj.getKey and curObj
	            	Stack<TransactionObj> stack = getHashPos( pTID );
	            	TransactionObj tobjz = new TransactionObj( 0, curObj.getKey(), curObj);
	            	stack.push( tobjz );
	            	///////
	            	Car anObj = new Car( location, count, price );
	            	anObj.setCount( curObj.getCount() + count );
	            	if ( price == 0 )
	                    anObj.setPrice( curObj.getPrice() );
	                	            	
	            	writeData( id, anObj.getKey(), anObj );
	            	Trace.info("RM::addCars(" + id + ") modified existing location " + location + ", count=" + anObj.getCount() + ", price=$" + price );
	            }
            }
            catch(Exception e){
            	e.printStackTrace();
            }
        } // else
        return(true);
    }


    // Delete cars from a location
    public boolean deleteCars(int id, String location, int pTID)
        throws RemoteException
    {
        return deleteItem(id, Car.getKey(location), pTID);
    }



    // Returns the number of empty seats on this flight
    public int queryFlight(int id, int flightNum, int pTID)
        throws RemoteException
    {
        return -1;//queryNum(id, Flight.getKey(flightNum));
    }

    // Returns the number of reservations for this flight. 
//    public int queryFlightReservations(int id, int flightNum)
//        throws RemoteException
//    {
//        Trace.info("RM::queryFlightReservations(" + id + ", #" + flightNum + ") called" );
//        RMInteger numReservations = (RMInteger) readData( id, Flight.getNumReservationsKey(flightNum) );
//        if ( numReservations == null ) {
//            numReservations = new RMInteger(0);
//        } // if
//        Trace.info("RM::queryFlightReservations(" + id + ", #" + flightNum + ") returns " + numReservations );
//        return numReservations.getValue();
//    }


    // Returns price of this flight
    public int queryFlightPrice(int id, int flightNum, int pTID)
        throws RemoteException
    {
        return -1;//queryPrice(id, Flight.getKey(flightNum));
    }


    // Returns the number of rooms available at a location
    public int queryRooms(int id, String location, int TID)
        throws RemoteException
    {
        return 0;//queryNum(id, Hotel.getKey(location));
    }


    
    
    // Returns room price at this location
    public int queryRoomsPrice(int id, String location, int TID)
        throws RemoteException
    {
        return -1;//queryPrice(id, Hotel.getKey(location));
    }


    // Returns the number of cars available at a location
    public int queryCars(int id, String location, int pTID)
        throws RemoteException
    {
        return queryNum(id, Car.getKey(location), pTID);
    }


    // Returns price of cars at this location
    public int queryCarsPrice(int id, String location, int pTID )
        throws RemoteException
    {
        return queryPrice(id, Car.getKey(location), pTID);
    }

    // Returns data structure containing customer reservation info. Returns null if the
    //  customer doesn't exist. Returns empty RMHashtable if customer exists but has no
    //  reservations.
    public RMHashtable getCustomerReservations(int id, int customerID)
        throws RemoteException
    {
        Trace.info("RM::getCustomerReservations(" + id + ", " + customerID + ") called" );
        Customer cust = (Customer) readData( id, Customer.getKey(customerID) );
        if ( cust == null ) {
            Trace.warn("RM::getCustomerReservations failed(" + id + ", " + customerID + ") failed--customer doesn't exist" );
            return null;
        } else {
            return cust.getReservations();
        } // if
    }

    // return a bill
    public String queryCustomerInfo(int id, int customerID)
        throws RemoteException
    {
        Trace.info("RM::queryCustomerInfo(" + id + ", " + customerID + ") called" );
        Customer cust = (Customer) readData( id, Customer.getKey(customerID) );
        if ( cust == null ) {
            Trace.warn("RM::queryCustomerInfo(" + id + ", " + customerID + ") failed--customer doesn't exist" );
            return "";   // NOTE: don't change this--WC counts on this value indicating a customer does not exist...
        } else {
                String s = cust.printBill();
                Trace.info("RM::queryCustomerInfo(" + id + ", " + customerID + "), bill follows..." );
                System.out.println( s );
                return s;
        } // if
    }

    // customer functions
    // new customer just returns a unique customer identifier
    
    public int newCustomer(int id)
        throws RemoteException
    {
        Trace.info("INFO: RM::newCustomer(" + id + ") called" );
        // Generate a globally unique ID for the new customer
        int cid = Integer.parseInt( String.valueOf(id) +
                                String.valueOf(Calendar.getInstance().get(Calendar.MILLISECOND)) +
                                String.valueOf( Math.round( Math.random() * 100 + 1 )));
        Customer cust = new Customer( cid );
        writeData( id, cust.getKey(), cust );
        Trace.info("RM::newCustomer(" + cid + ") returns ID=" + cid );
        return cid;
    }

    // I opted to pass in customerID instead. This makes testing easier
    public boolean newCustomer(int id, int customerID )
        throws RemoteException
    {
        Trace.info("INFO: RM::newCustomer(" + id + ", " + customerID + ") called" );
        Customer cust = (Customer) readData( id, Customer.getKey(customerID) );
        if ( cust == null ) {
            cust = new Customer(customerID);
            writeData( id, cust.getKey(), cust );
            Trace.info("INFO: RM::newCustomer(" + id + ", " + customerID + ") created a new customer" );
            return true;
        } else {
            Trace.info("INFO: RM::newCustomer(" + id + ", " + customerID + ") failed--customer already exists");
            return false;
        } // else
    }


    // Deletes customer from the database. 
    public boolean deleteCustomer(int id, int customerID)
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
            for (Enumeration e = reservationHT.keys(); e.hasMoreElements();) {        
                String reservedkey = (String) (e.nextElement());
                ReservedItem reserveditem = cust.getReservedItem(reservedkey);
                Trace.info("RM::deleteCustomer(" + id + ", " + customerID + ") has reserved " + reserveditem.getKey() + " " +  reserveditem.getCount() +  " times"  );
                ReservableItem item  = (ReservableItem) readData(id, reserveditem.getKey());
                Trace.info("RM::deleteCustomer(" + id + ", " + customerID + ") has reserved " + reserveditem.getKey() + "which is reserved" +  item.getReserved() +  " times and is still available " + item.getCount() + " times"  );
                item.setReserved(item.getReserved()-reserveditem.getCount());
                item.setCount(item.getCount()+reserveditem.getCount());
            }
            
            // remove the customer from the storage
            removeData(id, cust.getKey());
            
            Trace.info("RM::deleteCustomer(" + id + ", " + customerID + ") succeeded" );
            return true;
        } // if
    }



    /*
    // Frees flight reservation record. Flight reservation records help us make sure we
    // don't delete a flight if one or more customers are holding reservations
    public boolean freeFlightReservation(int id, int flightNum)
        throws RemoteException
    {
        Trace.info("RM::freeFlightReservations(" + id + ", " + flightNum + ") called" );
        RMInteger numReservations = (RMInteger) readData( id, Flight.getNumReservationsKey(flightNum) );
        if ( numReservations != null ) {
            numReservations = new RMInteger( Math.max( 0, numReservations.getValue()-1) );
        } // if
        writeData(id, Flight.getNumReservationsKey(flightNum), numReservations );
        Trace.info("RM::freeFlightReservations(" + id + ", " + flightNum + ") succeeded, this flight now has "
                + numReservations + " reservations" );
        return true;
    }
    */

    
    // Adds car reservation to this customer. 
    public boolean reserveCar(int id, int customerID, String location, int pTID)
        throws RemoteException
    {
        return reserveItem(id, customerID, Car.getKey(location), location, pTID);
    }


    // Adds room reservation to this customer. 
    public boolean reserveRoom(int id, int customerID, String location, int TID)
        throws RemoteException
    {
        return false;//reserveItem(id, customerID, Hotel.getKey(location), location);
    }
    // Adds flight reservation to this customer.  
    public boolean reserveFlight(int id, int customerID, int flightNum, int TID)
        throws RemoteException
    {
        return false;//reserveItem(id, customerID, Flight.getKey(flightNum), String.valueOf(flightNum));
    }
    
    // Reserve an itinerary 
    public boolean itinerary(int id,int customer,Vector flightNumbers,String location,boolean Car,boolean Room)
        throws RemoteException
    {
        return false;
    }
    public RMItem getObj( int id, String key ){
    	return readData( id, key );
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
	    else if (argument.compareToIgnoreCase("deleteRes")==0)
	    	return 27;
	    else if (argument.compareToIgnoreCase("voterequest")==0)
	    	return 28;
	    else
	        return 666;
    }    
    
    public boolean deleteRes( int id, String key, int cnt, int pTID ){//To maintain item count when a customer is deleted at Middleware
    	ReservableItem item = (ReservableItem)readData(id, key);
    	synchronized( item ){//locking on reserveableItem object
        if ( item == null ) {
            //Trace.warn("RM::deleteReservation( " + id + ", " + key+", " +location+") failed--item doesn't exist" );
            return false;
        } else if (item.getCount()==0) {
            //Trace.warn("RM::deleteReservation( " + id + ", " + customerID + ", " + key+", " + location+") failed--No more items" );
            return false;
        } else {            
            // increase the number of available items in the storage
        	try{
	            if( lm.Lock( pTID, item.getKey(), 1 ) ){
	            	//Abort: writeData with curObj.getKey() and curObj
	            	Stack<TransactionObj> stack = getHashPos( pTID );
	            	TransactionObj tobjz = new TransactionObj( 2, item.getKey(), item );
	            	tobjz.cnt = cnt;
	            	stack.push( tobjz );	            	
	            	//
	            	item.setReserved(item.getReserved() - cnt);
	            	item.setCount(item.getCount() + cnt);
	            	System.out.println("Reservation deleted.");
	            	return true;
	            }
        	}
        	catch(DeadlockException d){
        		d.printStackTrace();
        	}
        	}
    	}
    	return false;
    }
    
    void sendMessage(String msg){
		try{
			outputMW.writeObject(msg);
			outputMW.flush();
			System.out.println("RM To MW > " + msg); //This is just to keep track of who's saying what
		}
		catch(Exception e){
			System.err.println(e);
		}
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
    		else if( tobjz.type == 1 )
    			removeData( 1, tobjz.key );
    		else{
    			ReservableItem x = (ReservableItem) tobjz.obj;
        		x.setReserved(x.getReserved() + tobjz.cnt);
        		x.setCount(x.getCount() - tobjz.cnt);
        		writeData( 1, tobjz.key, (RMItem) x);
        	}
    		st.pop();
    		lm.UnlockAll(pTID);
            transHash.remove(pTID);
            tidBase.remove(pTID);
            System.out.println("Transaction #"+pTID+" aborted.");
    	}
    }
}