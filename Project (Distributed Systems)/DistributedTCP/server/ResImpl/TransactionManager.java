package ResImpl;
import java.util.*;

public class TransactionManager {
	public static Integer TID = 0;
	public static Hashtable <Integer, TransactionObj> htid = new Hashtable< Integer, TransactionObj> ();
	
	TransactionManager(){
		
	}
	public int start(){
		synchronized(TID){
			TID++;
		}
		TransactionObj tobj;
		return TID.intValue();
	}
	
	public void add( int key, TransactionObj tobj ){
		htid.put(key, tobj);
	}	
}
