#include <slack/err.h>
#include <netinet/in.h>
#include <string.h>
#include <pthread.h>
#include "protocols.h"
#include "arp.h"
#include "gnet.h"
#include "moduledefs.h"
#include "grouter.h"
#include "packetcore.h"
#include "ospf.h"
#include "gnet.h"
#include "mtu.h"
#include "ip.h"
#include "dijkstra.h"

int numOfRouters, N;
LSA_Packet *lstable;
uchar IDList[MAXNODES + 10][4];
int G[MAXNODES + 10][MAXNODES + 10];
int D[MAXNODES + 10], parent[MAXNODES+10], visited[MAXNODES+10];

void dijkstraInit( LSA_Packet *lst, int tableSize ){
    numOfRouters = tableSize;
    lstable = (LSA_Packet *)malloc( tableSize * sizeof(LSA_Packet));
    memcpy(lstable, lst, tableSize * sizeof(LSA_Packet));
    //printLSUD();
    createGraph();
    dijkstra();
    printRT();
}

int getID( uchar *ip ){
    int i;
    for( i = 1; i < N; i++ ){
        if( COMPARE_IP( ip, IDList[i] ) == 0 )
            return i;
    }
    COPY_IP(IDList[i], ip );
    char tmpbuf[MAX_TMPBUF_LEN];
    //printf("NEW %s\n", IP2Dot(tmpbuf, ip));
    ++N;
    return i;
}

void createGraph(){
    int i, j, k;
    ospf_LSU *lslnkz = lstable->data;
    N = 1;
    //initializing adjacency matrix
    for( i = 0; i < MAXNODES + 10; i++ )
        for( j = 0; j < MAXNODES + 10; j++ )
            G[i][j] = 0;
    COPY_IP( IDList, lstable->advertRouterIp );
    /*for( i = 0; i < numOfRouters; i++ ){
        for( j = 0; j < lslnkz->numOfLinks; j++ ){
           getID(lslnkz->links[j].linkID);//Maybe use & here
        }          
    }*/
    //int prevN = N;
    for( j = 0; j < lslnkz->numOfLinks; j++ ){
        G[0][getID(&(lslnkz->links[j].linkID))] = 1;
        //if( N > prevN ) prevN = N, printf("N Incremented at %dA\n", j);
    }
    //maybe do the -1 thing here to be safe
    ospf_LSU *lss;
    LSA_Packet *tst = (LSA_Packet *)lstable;
    for( i = 1; i < numOfRouters; i++ ){
        ++tst;
        lss = tst->data;
        for( j = 0; j < lss->numOfLinks; j++ ){
           int x = getID(&(lss->links[j].linkID));
           for( k = j + 1; k < lss->numOfLinks; k++ ){
               int y = getID(&(lss->links[k].linkID));
               G[x][y] = 1;
               G[y][x] = 1;
           }
        }  
    }
    /*printf("Number of Nodes: %d\n", N);
    printf("0 : Self\n");
    char tmpbuf[MAX_TMPBUF_LEN], tmpbuf2[MAX_TMPBUF_LEN];
    for( j = 0; j < N; j++ ){
        for( i = 0; i < N; i++ ){
            if( G[j][i] == 1 )
                printf("%s : %s\n", IP2Dot( tmpbuf, IDList[j] ), IP2Dot( tmpbuf2, IDList[i] ) );
        }
    }
    printf("\n");*/
}

void dijkstra(){
    int i, j;
    D[0] = 0, parent[0] = 0, visited[0] = 0;
    for( i = 1; i < N; i++ )
        D[i] = 10000, parent[i] = 0, visited[i] = 0;
    int current = 0, minD;
    while(1){
        for( j = 0; j < N; j++ ){
            if( G[current][j] == 1 && visited[j] == 0 ){
                if(current != j && (D[current] + G[current][j] < D[j])){               
                        //printf("j %d current %d parent[current] %d\n", j, current, parent[current]);
                        //printf("CHANGE D[j] %d D[current] %d parent[j] %d\n", D[j], D[current], parent[j]);
                        D[j] = D[current] + G[current][j];
                        parent[j] = current;
                }
            }
        }
        visited[current] = 1;
        minD = 10000, current = -1;
        for( i = 0; i < N; i++ ){
            if( visited[i] == 0 && D[i] < minD )
                minD = D[i], current = i;
        }
        if( current == -1 )
            break;
    }
}

void printRT(){
    int i, j;
    char tmpbuf[MAX_TMPBUF_LEN], tmpbuf2[MAX_TMPBUF_LEN];
    printf("\nNetwork ID          Cost          Next Hop\n");
    printf("------------------------------------------\n");
    for( i = 1; i < N; i++ ){
        printf("%s", IP2Dot( tmpbuf, IDList[i] ) );
        if( D[i] != 10000)
                printf("           %d", D[i]);
        else
            printf("  Unreachable", D[i]);
        if( parent[i] == 0 )
            printf("        0.0.0.0\n");
        else
            printf("        %s\n", IP2Dot( tmpbuf2, IDList[parent[i]] ));
    }
    printf("\n");
}

void printLSUD(){
    int i, j;
    char tmpbuf[MAX_TMPBUF_LEN], tmpbuf2[MAX_TMPBUF_LEN];
    LSA_Packet *lsp = (LSA_Packet *)lstable;
    for( i = 0; i < numOfRouters; i++, lsp++ ){
        ospf_LSU *lsu = lsp->data;
        lnk *LList = lsu->links;
        printf("DIJKSTRA.C Link State ID: %s Number of Links: %d\n", IP2Dot(tmpbuf, lsp->advertRouterIp), lsu->numOfLinks );
        for( j = 0; j < lsu->numOfLinks; j++, LList++ ){
            printf("Edge between %s and %s\n", IP2Dot(tmpbuf, &(LList->linkID)), IP2Dot(tmpbuf2, &(LList->linkData)));
        }
        printf("\n");
    }
}