package ResImpl;
import java.util.*;

public class TransactionObj {
	protected int type;//0 for writeData, 1 for removeData, 2 for changing count [e.g. delete reservation]
	protected String key;
	protected RMItem obj;
	protected int cnt;
	TransactionObj( int a, String b, RMItem c ){
		this.type = a;
		this.key = b;
		this.obj = c;
	}
}
