/*
 * gnet.c (GINI network adapter module)
 * AUTHOR: Muthucumaru Maheswaran
 *         Borrows some code from the UML_ADAPTER written by
 *         Weiling Xu
 * DATE: Last revised on June 22, 2008
 * VERSION: 1.0
 */

#include "devicedefs.h"
#include "gnet.h"
#include "arp.h"
#include "ip.h"
#include "grouter.h"
#include "device.h"
#include "message.h"
#include "ethernet.h"
#include "tap.h"
#include "tapio.h"
#include "protocols.h"
#include <slack/err.h>
#include <sys/time.h>
#include <netinet/in.h>
#include "routetable.h"
#include "ospf.h"
#include <time.h>   

extern route_entry_t route_tbl[MAX_ROUTES];

interface_array_t netarray;
devicearray_t devarray;
arp_entry_t arp_cache[ARP_CACHE_SIZE];


/*----------------------------------------------------------------------------------
 *             D E V I C E  M A N A G E M E N T  F U N C T I O N S
 *---------------------------------------------------------------------------------*/


/*
 * Initialize the Device driver table
 * This process creates the device drivers for all registered
 * network device types. Not all device drivers are used at
 * any time. For example, if no wireless devices are presented,
 * the wireless LAN (wlan) device driver may not be invoked.
 * On the other hand, it is not possible to start a network interface
 * for which the supporting device driver is not pre-registered.
 */
int createAllDevices(devicearray_t *dev)
{
	int i;

	LIST_OF_DEVICES;


	dev->count = sizeof(devdir)/sizeof(devicedirectory_t);
	dev->elem = (device_t *) malloc(dev->count * sizeof(device_t));
	for (i = 0; i < dev->count; i++)
	{
		verbose(2, "[createAllDevices]:: Installing device driver for %s", devdir[i].devname);
		strcpy(dev->elem[i].devname, devdir[i].devname);
		strcpy(dev->elem[i].devdesc, devdir[i].devdesc);
		dev->elem[i].fromdev = devdir[i].fromdev;
		dev->elem[i].todev = devdir[i].todev;
	}

	return EXIT_SUCCESS;
}


/*
 * finds the device driver... by searching the
 * devarray global variable.
 */
device_t *findDeviceDriver(char *dev_type)
{
	int i;

	for (i = 0; i < devarray.count; i++)
		if (strstr(dev_type, devarray.elem[i].devname) != NULL)
			return &(devarray.elem[i]);
	verbose(1, "[findDeviceDriver]:: WARNING! Could not find device driver for %s ", dev_type);
	return NULL;
}


/*
 * Initialize the virtual physical layer...
 * At this point, we are just setting up the FIFO for interfacing
 * with "wireshark". Other functions could be hooked up here?
 */
void initPhysicalLayer(char *config_dir, char *rname)
{
	vpl_init(config_dir, rname);
}



/*----------------------------------------------------------------------------------
 *             I N T E R F A C E  M A N A G E M E N T  F U N C T I O N S
 *---------------------------------------------------------------------------------*/


/*
 * Initialize interface arrays and device arrays
 */
void GNETInitInterfaces()
{
	int i;

	createAllDevices(&devarray);
	netarray.count = 0;
	for (i=0; i < MAX_INTERFACES; i++)
		netarray.elem[i] = NULL;
}


/*
 * shutdown all interfaces.. used at router shutdown..
 */
void haltInterfaces()
{
	int i;

	for (i = 0; i < MAX_INTERFACES; i++)
		destroyInterface(netarray.elem[i]);
}



int getNextInterfaceID(void)
{
	int nextid;

	nextid = netarray.count++;
	if (nextid >= MAX_INTERFACES)
	{
		error("[getNextInterfaceID]:: Cannot create another interface: No empty slots ");
		return -1;
	}
	return nextid;
}


/*
 * insert the given interface into the Interface table
 */
void GNETInsertInterface(interface_t *iface)
{
	int ifid = iface->interface_id;

	if (netarray.elem[ifid] != NULL)
	{
		verbose(1, "[insertInterface]:: Cannot create interface.. delete exiting one first ");
		return;
	}
	netarray.elem[ifid] = iface;
	netarray.count++;
}


/*
 * returns EXIT_SUCCESS if the element was removed.
 * Otherwise returns EXIT_FAILURE.
 */
int deleteInterface(int indx)
{

	if (indx > MAX_INTERFACES)
	{
		verbose(1, "[deleteInterface]:: Specified index: Out of range ");
		return EXIT_FAILURE;
	}

	// release memory;
	free(netarray.elem[indx]);

	// delete slot...
	netarray.elem[indx] = NULL;
	if (netarray.count > 0) netarray.count--;

	return EXIT_SUCCESS;
}



/*
 * findInterface... this is pretty simple because the interface table
 * is direct mapped! We need to search the table if we want to find interfaces
 * by IP address or MAC address or any other parameter.
 */

interface_t *findInterface(int indx)
{
	return netarray.elem[indx];
}


void printHorLine(int mode)
{
	int i, imax;

	switch (mode)
	{
	case NORMAL_LISTING:
		imax = 60;
		break;
	case VERBOSE_LISTING:
		imax = 100;
		break;
	}
	for (i = 0; i < imax; i++)
		printf("-");
	printf("\n");
}



/*
 * list Interfaces: list all active interfaces...
 */
void printInterfaces(int mode)
{
	int i;
	interface_t *ifptr;
	char tmpbuf[MAX_TMPBUF_LEN];

	printf("\n\n");
	printHorLine(mode);
	printf("      I N T E R F A C E  T A B L E \n");
	printHorLine(mode);
	switch (mode)
	{
		case NORMAL_LISTING:
			printf("Device\tState\tIP address\tMAC address\t\tMTU\n");
			break;
		case VERBOSE_LISTING:
			printf("Int.\tState/Mode\tDevice\tIP address\tMAC address\t\tMTU\tSocket Name\tThread ID\n");
			break;
	}
	for (i = 0; i < MAX_INTERFACES; i++)
		if (netarray.elem[i] != NULL)
		{
			ifptr = netarray.elem[i];
			switch (mode)
			{
				case NORMAL_LISTING:
					printf("%s\t%c\t%s\t%s\t%d\n", ifptr->device_name,
					       ifptr->state, IP2Dot(tmpbuf, ifptr->ip_addr),
					       MAC2Colon((tmpbuf+20), ifptr->mac_addr),
					       ifptr->device_mtu);
					break;
				case VERBOSE_LISTING:
					printf("%d\t%c%c\t\t%s\t%s\t%s\t%d\t%s\t%d\n", ifptr->interface_id,
					       ifptr->state, ifptr->mode,
					       ifptr->device_name,
					       IP2Dot(tmpbuf, ifptr->ip_addr),
					       MAC2Colon((tmpbuf+20), ifptr->mac_addr),
					       ifptr->device_mtu,
					       ifptr->sock_name,
					       (int) ifptr->threadid);
					break;
			}
		}
	printHorLine(mode);
	printf("\n\n");
	return;
}


/*
 * Create an interface data structure and fill it with relevant information.
 * This routine does not setup any external elements.. other than finding
 * the device driver and linking it. We need the correct device driver for
 * the interface.. because a particular device would provide its own I/O routines.
 */
interface_t *newInterfaceStructure(char *vsock_name, char *device,
				   uchar *mac_addr, uchar *nw_addr, int iface_mtu)
{
	int iface_id;
	interface_t *iface;


	iface_id = gAtoi(device);

	// fill in the interface structure...
	iface = (interface_t *) malloc(sizeof(interface_t));
	if (iface == NULL)
	{
		fatal("[createInterfaceStructure]:: Memory allocation error ");
		return NULL;
	}

	verbose(2, "[createInterfaceStructure]:: Memory allocated for interface structure ");

	bzero((void *)iface, sizeof(interface_t));
	iface->interface_id = iface_id;
	iface->mode = IFACE_CLIENT_MODE;
	iface->state = INTERFACE_DOWN;                           // start in the DOWN state
	sscanf(device, "%[a-z]", iface->device_type);
	strcpy(iface->device_name, device);
	strcpy(iface->sock_name, vsock_name);
	COPY_MAC(iface->mac_addr, mac_addr);
	COPY_IP(iface->ip_addr, nw_addr);
	iface->device_mtu = iface_mtu;

	verbose(2, "[makeInterface]:: Searching the device driver for %s ", iface->device_type);
	iface->devdriver = findDeviceDriver(iface->device_type);
	iface->iarray = &netarray;

	return iface;
}


/*
 * GNETMakeEthInterface: this returns NULL if an interface cannot be
 * created. Otherwise, it returns a pointer to the newly created
 * structure that contains all the details for the interface. This
 * function also creates a thread for each activated interface. This
 * thread is responsible for listening for any incoming packets. There is
 * no thread created for outgoing packets!
 * ARGUMENTS: vsock_name: string name of the vpl_socket
 * 			  device:  eth1, eth2 (device name and device IDs are separated from this)
 * 			  mac_addr: hardware address of the interface
 * 			  nw_addr: network address of the interface (IPv4 by default)
 * 			  iface_mtu: MTU value for interface
 *
 * RETURNS: a pointer to the interface on success and NULL on failure
 */

interface_t *GNETMakeEthInterface(char *vsock_name, char *device,
			   uchar *mac_addr, uchar *nw_addr, int iface_mtu, int cforce)
{
	vpl_data_t *vcon;
	interface_t *iface;
	int iface_id, thread_stat;
	char tmpbuf[MAX_TMPBUF_LEN];
	vplinfo_t *vi;
	pthread_t threadid;


	verbose(2, "[GNETMakeEthInterface]:: making Interface for [%s] with MAC %s and IP %s",
		device, MAC2Colon(tmpbuf, mac_addr), IP2Dot((tmpbuf+20), nw_addr));

	iface_id = gAtoi(device);
	vi = (vplinfo_t *)malloc(sizeof(vplinfo_t));
	if (findInterface(iface_id) != NULL)
	{
		verbose(1, "[GNETMakeEthInterface]:: device %s already defined.. ", device);
		return NULL;
	}
	else
	{
		// setup the interface..
		iface = newInterfaceStructure(vsock_name, device,
					      mac_addr, nw_addr, iface_mtu);

		/*
		 * try connection (as client). if it fails and force client flag
		 * is set, then return NULL. Otherwise, try server connection,
		 * if this fails, print error and return NULL
		 */

		verbose(2, "[GNETMakeEthInterface]:: trying to connect to %s..", vsock_name);
		if ((vcon = vpl_connect(vsock_name)) == NULL)
		{
			verbose(2, "[GNETMakeEthInterface]:: connecting as server.. ");
			// if client mode is forced.. fail here.. cannot do much!
			if (cforce)
			{
				verbose(1, "[GNETMakeEthInterface]:: unable to make network connection...");
				return NULL;
			}
			// try making a server connection...
			// now that the client connection has failed

			if ((vcon = vpl_create_server(vsock_name)) == NULL)
			{
				verbose(1, "[GNETMakeEthInterface]:: unable to make server connection.. ");
				return NULL;
			}

			iface->mode = IFACE_SERVER_MODE;
			vi->vdata = vcon;
			vi->iface = iface;
			thread_stat = pthread_create(&(iface->sdwthread), NULL,
						     (void *)delayedServerCall, (void *)vi);
			if (thread_stat != 0)
				return NULL;

			// return for now.. the thread spawned above will change the
			// interface state when the remote node makes the connection.
			return iface;
		}

		verbose(2, "[GNETMakeEthInterface]:: VPL connection made as client ");

		// fill in the rest of the interface
		iface->iface_fd = vcon->data;
		iface->vpl_data = vcon;

		upThisInterface(iface);
		return iface;
	}
}


/*
 * GNETMakeTapInterface: this returns NULL if an interface cannot be
 * created. We use "tap0" as the "tap" device name. The device number is
 * fixed at 0. The eth device starts from 1. There is no eth0!
 *
 * RETURNS: a pointer to the interface on success and NULL on failure
 */

interface_t *GNETMakeTapInterface(char *device, uchar *mac_addr, uchar *nw_addr)
{
	vpl_data_t *vcon;
	interface_t *iface;
	int iface_id;
	char tmpbuf[MAX_TMPBUF_LEN];


	verbose(2, "[GNETMakeTapInterface]:: making Interface for [%s] with MAC %s and IP %s",
		device, MAC2Colon(tmpbuf, mac_addr), IP2Dot((tmpbuf+20), nw_addr));

	iface_id = gAtoi(device);

	if (findInterface(iface_id) != NULL)
	{
		verbose(1, "[GNETMakeTapInterface]:: device %s already defined.. ", device);
		return NULL;
	}
	else
	{
		// setup the interface.. with default MTU of 1500 we cannot set it at this time.
		iface = newInterfaceStructure(device, device, mac_addr, nw_addr, 1500);

		/*
		 * try connection (as client). only option here...
		 */
		verbose(2, "[GNETMakeTapInterface]:: trying to connect to %s..", device);
		if ((vcon = tap_connect(device)) == NULL)
		{
			verbose(1, "[GNETMakeTapInterface]:: unable to connect to %s", device);
			return NULL;
		}

		// fill in the rest of the interface
		iface->iface_fd = vcon->data;
		iface->vpl_data = vcon;

		upThisInterface(iface);
		return iface;
	}
}



void *delayedServerCall(void *arg)
{
	vplinfo_t *vi = (vplinfo_t *)arg;
	vpl_data_t *vcon = vi->vdata;
	interface_t *iface = vi->iface;

	pthread_setcanceltype(PTHREAD_CANCEL_ASYNCHRONOUS, NULL);
	while (1)
	{
		if (vpl_accept_connect(vcon) >= 0)
		{
			if (iface->state == INTERFACE_UP)
				downThisInterface(iface);
			verbose(2, "[delayedServerCall]:: bringing up the interface ...");
			iface->iface_fd = vcon->data;
			iface->vpl_data = vcon;
			upThisInterface(iface);
		}
	}
	return NULL;
}


/*
 * destroyInterface by Index...
 */
int destroyInterfaceByIndex(int indx)
{
	return destroyInterface(findInterface(indx));
}


/*
 * destroyInterface(): remove the specified interface from the router.
 * The router should remove associated route table information, ARP entries,
 * and stop the threads (only fromXDevice thread).
 */
int destroyInterface(interface_t *iface)
{

	// nothing to do if iface is NULL
	if (iface == NULL)
		return EXIT_FAILURE;

	verbose(2, "[destroyInterface]:: entering the function..");
	// remove the routing table entries...
	deleteRouteEntryByInterface(route_tbl, iface->interface_id);

	// remove the ARP table entries
	ARPDeleteEntry(iface->ip_addr);

	verbose(2, "[destroyInterface]:: cancelling the fromdev handler.. ");
	if (iface->state == INTERFACE_UP)
	{
		pthread_cancel(iface->threadid);        // cancel the running thread
		// close socket
		close(iface->iface_fd);
	}

	verbose(2, "[destroyInterface]:: cancelling the shadow thread.. ");
	if (iface->mode == IFACE_SERVER_MODE)
	{
		pthread_cancel(iface->sdwthread);      // cancel the shadow thread
		unlink(iface->sock_name);
	}

	// remove interface from table...
	deleteInterface(iface->interface_id);

	return EXIT_SUCCESS;
}



/*
 * change the MTU value of the interface
 */
int changeInterfaceMTU(int index, int new_mtu)
{
	interface_t *iface;

	iface = findInterface(index);
	if (iface == NULL)
	{
		error("[changeInterface]:: Interface %d not found.. unable to change MTU ", index);
		return EXIT_FAILURE;
	}
	iface->device_mtu = new_mtu;
	return EXIT_SUCCESS;
}


/*
 * change the interface state to up -- of this interface
 */
int upThisInterface(interface_t *iface)
{
	int thread_stat;

	iface->state = INTERFACE_UP;
	thread_stat = pthread_create(&(iface->threadid), NULL,
				     (void *)iface->devdriver->fromdev, (void *)iface);
	if (thread_stat != 0)
		return EXIT_FAILURE;

	return EXIT_SUCCESS;
}



/*
 * change the interface state to down -- of this interface
 */
int downThisInterface(interface_t *iface)
{
	int status;

	status = pthread_cancel(iface->threadid);
	iface->state = INTERFACE_DOWN;

	if (status == 0)
		return EXIT_SUCCESS;
	else
		return EXIT_FAILURE;
}




/*
 * change the interface state to up
 */
int upInterface(int index)
{
	interface_t *iface;

	iface = findInterface(index);
	if (iface == NULL)
	{
		error("[upInterface]:: Interface %d not found.. unable to bring up ", index);
		return EXIT_FAILURE;
	}

	return upThisInterface(iface);
}


/*
 * change the interface state to down
 */
int downInterface(int index)
{
	interface_t *iface;

	iface = findInterface(index);
	if (iface == NULL)
	{
		error("[downInterface]:: Interface %d not found ..unable to take down ", index);
		return EXIT_FAILURE;
	}

	return downThisInterface(iface);
}



/*----------------------------------------------------------------------------------
 *             A R P  C A C H E  M A N A G E M E N T  F U N C T I O N S
 *---------------------------------------------------------------------------------*/

/*
 * return the ARP cache key. This value is used to
 * index into the direct mapped ARP cache. Direct mapped cache
 * is used for simplicity.. Should we use more complex organizations??
 */

int getARPCacheKey(uchar *ip)
{
	unsigned int sum = 0;

	sum = ip[0] + ip[1] + ip[2] + ip[3];
	return sum % ARP_CACHE_SIZE;
}


void GNETInitARPCache(void)
{
	int i;

	for (i = 0; i < ARP_CACHE_SIZE; i++)
		arp_cache[i].is_empty = TRUE;
}


/*
 * lookup the given ip_addr in the ARP cache.
 * copy the MAC address in mac_addr and return TRUE
 * otherwise return FALSE -- mac_addr is undefined in this case.
 */
int lookupARPCache(uchar *ip_addr, uchar *mac_addr)
{
	int key;

	key = getARPCacheKey(ip_addr);
	if ((arp_cache[key].is_empty == FALSE) &&
	    (COMPARE_IP(arp_cache[key].ip_addr, ip_addr) == 0))
	{
		COPY_MAC(mac_addr, arp_cache[key].mac_addr);
		return TRUE;
	}

	return FALSE;
}


void putARPCache(uchar *ip_addr, uchar *mac_addr)
{
	int key;

	key = getARPCacheKey(ip_addr);
	arp_cache[key].is_empty = FALSE;
	COPY_IP(arp_cache[key].ip_addr, ip_addr);
	COPY_MAC(arp_cache[key].mac_addr, mac_addr);
}



void printARPCache(void)
{
	int i;
	char tmpbuf[MAX_TMPBUF_LEN];

	printf("\n-----------------------------------------------------------\n");
	printf("      A R P  C A C H E \n");
	printf("-----------------------------------------------------------\n");
	printf("Index\tIP address\tMAC address \n");

	for (i = 0; i < MAX_ARP; i++)
		if (arp_cache[i].is_empty == FALSE)
			printf("%d\t%s\t%s\n", i, IP2Dot(tmpbuf, arp_cache[i].ip_addr),
			       MAC2Colon((tmpbuf+20), arp_cache[i].mac_addr));
	printf("-----------------------------------------------------------\n");
	return;
}




/*----------------------------------------------------------------------------------
 *                         M A I N  F U N C T I O N S
 *---------------------------------------------------------------------------------*/


void GNETHalt(int gnethandler)
{
	verbose(2, "[gnetHalt]:: Shutting down GNET handler.. \n");
	haltInterfaces();
	pthread_cancel(gnethandler);
}


/*
 * GNETInit: Initialize the GNET subsystem..
 * Initialize the necessary data structures. Setup a thread to read the output Queue and
 * handle the packet. Note that some packets can have valid ARP addresses. Such packets
 * are sent to the device. Packets that do not have valid ARP addresses need ARP resolution
 * to get the valid MAC address. They are buffered in the ARP buffer (within the ARP routines)
 * and injected back into the Output Queue once the ARP reply from a remote machine comes back.
 * This means a packet can go through the Output Queue two times.
 */
int GNETInit(int *ghandler, char *config_dir, char *rname, simplequeue_t *sq)
{
	int thread_stat;

	// do the initializations...
	vpl_init(config_dir, rname);
	GNETInitInterfaces();
 	GNETInitARPCache();

	thread_stat = pthread_create((pthread_t *)ghandler, NULL, GNETHandler, (void *)sq);
	if (thread_stat != 0)
		return EXIT_FAILURE;
	else
		return EXIT_SUCCESS;

}

int pktCnt;

void throughputCalc(){
    pthread_t threadTimer;
    int thread_stat = pthread_create(&(threadTimer), NULL, (void *) tcalc, (void *) NULL);
}

void *tcalc(){
    printf("Calculating throughput...\n");
    clock_t t;
    int i, calcCnt = 0;
    double throughput[10];
    while(1){
        pktCnt = 0;
        //t = clock();
        sleep(2);
        int cnt = pktCnt;
        //t = clock() - t;
        double tme = ((double)t) / CLOCKS_PER_SEC;
        throughput[calcCnt++] = ((double)cnt) / 2.0;
        printf("Throughput %d: %lf  %lf\n", calcCnt, throughput[calcCnt - 1], tme);
        if( calcCnt == 10 ){
            //calc average
            double avg = 0;
            for( i = 0; i < 10; i++ ) 
                avg += throughput[i];
            
            avg /= (double)10;
            //calc standard deviation
            double sd = 0;
            for( i = 0; i < 10; i++ )
                sd += (throughput[i] - avg) * (throughput[i] - avg);
            
            sd = sqrt(sd);
            printf("Throughput: %lf\n Standard Deviation: %lf\n\n", avg, sd );
            break;
        }        
    }
}

void *GNETHandler(void *outq)
{
	char tmpbuf[MAX_NAME_LEN];
	interface_t *iface;
	uchar mac_addr[6];
	simplequeue_t *outputQ = (simplequeue_t *)outq;
	gpacket_t *in_pkt;
	int inbytes, cached, thread_stat;
        pktCnt = 0;
        //pthread_t threadTimer;
        //thread_stat = pthread_create(&(threadTimer), NULL, (void *) throughputCalc, (void *) NULL);
	pthread_setcanceltype(PTHREAD_CANCEL_ASYNCHRONOUS, NULL);       // die as soon as cancelled
        memset( UDPports, 0, sizeof(UDPports) );
        socketCnt = 0;
	while (1)
	{
		verbose(2, "[gnetHandler]:: Reading message from output Queue..");
		if (readQueue(outputQ, (void **)&in_pkt, &inbytes) == EXIT_FAILURE)
			return NULL;
		verbose(2, "[gnetHandler]:: Recvd message pkt ");
		pthread_testcancel();

		if ((iface = findInterface(in_pkt->frame.dst_interface)) == NULL)
		{
			error("[gnetHandler]:: Packet dropped, interface [%d] is invalid ", in_pkt->frame.dst_interface);
			continue;
		} else if (iface->state == INTERFACE_DOWN)
		{
			error("[gnetHandler]:: Packet dropped! Interface not up");
			continue;
		}

		// we have a valid interface handle -- iface.
		COPY_MAC(in_pkt->data.header.src, iface->mac_addr);

		if (in_pkt->frame.arp_valid == TRUE)
			putARPCache(in_pkt->frame.nxth_ip_addr, in_pkt->data.header.dst);
		else if (in_pkt->frame.arp_bcast != TRUE)
		{
			if ((cached = lookupARPCache(in_pkt->frame.nxth_ip_addr,
						     mac_addr)) == TRUE)
				COPY_MAC(in_pkt->data.header.dst, mac_addr);
			else
			{
                            ARPResolve(in_pkt);
                                continue;
			}
		}
                else{
                    ip_packet_t *ip_pkt2 = (ip_packet_t *)in_pkt->data.data;
                    if( ip_pkt2->ip_prot == OSPF_PROTOCOL ){
                        //ospf_packet_t *ospfpt = (ospf_packet_t *)(ip_pkt2 + 20);
                        //if( ospfpt->type == HELLO ){ Because we BROADCAST to everyone
                            uchar bcast_mac[6] = MAC_BCAST_ADDR;
                            COPY_MAC( in_pkt->data.header.dst, bcast_mac );
                        //}
                    }
                }
                pktCnt++;
		iface->devdriver->todev((void *)in_pkt);

	}
}

