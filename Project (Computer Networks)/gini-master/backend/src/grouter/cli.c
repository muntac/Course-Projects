/*
 * cli.c (Command line handler for the GINI router)
 * This file contains the functions that implement the CLI.
 * AUTHOR: Original version written by Weiling Xu
 *         Revised by Muthucumaru Maheswaran
 * DATE:   Revised on December 24, 2004
 *
 * The CLI is used as a configuration file parser
 * as well. Right now the CLI module is only capable
 * of parsing a very simple format and limited command set...
 * Future development should make the CLI more versatile?
 * The CLI defers unknown command to the UNIX system at this point.
 */

#include "helpdefs.h"
#include "cli.h"
#include "gnet.h"
#include "grouter.h"
#include <stdio.h>
#include <strings.h>
#include "grouter.h"
#include "routetable.h"
#include "mtu.h"
#include "message.h"
#include "classifier.h"
#include "filter.h"
#include "classspec.h"
#include "packetcore.h"
#include <slack/err.h>
#include <slack/std.h>
#include <slack/prog.h>
#include <slack/err.h>
#include <stdlib.h>
#include <readline/readline.h>
#include <readline/history.h>
#include "ospf.h"


Map *cli_map;
Mapper *cli_mapper;
static char *cur_line = (char *)NULL;       // static variable for holding the line

extern FILE *rl_instream;
extern router_config rconfig;

extern route_entry_t route_tbl[MAX_ROUTES];
extern mtu_entry_t MTU_tbl[MAX_MTU];
extern classlist_t *classifier;
extern filtertab_t *filter;
extern pktcore_t *pcore;

/*
 * This is the main routine of the CLI. Everything starts here.
 * The CLI registers and commands into a hash table and forks a thread to
 * handle the command line.
 */
void throughputCmd();
void udpCmd();

int CLIInit(router_config *rarg)
{

	int stat, *jstat;

	if (!(cli_map = map_create(free)))
		return EXIT_FAILURE;

	/*
	 * Disable certain signals such as Ctrl+C.. make the shell a little stable
	 */
	redefineSignalHandler(SIGINT, dummyFunction);
	redefineSignalHandler(SIGQUIT, dummyFunction);
	redefineSignalHandler(SIGTSTP, dummyFunction);

	verbose(2, "[cliHandler]:: Registering CLI commands in the command table ");
	/*
	 * Register the commands allowed in the CLI. Each command is implemented by a
	 * function. The function is inserted into the command registary and picked up
	 * when the leading string is typed in the CLI.
	 */
	registerCLI("help", helpCmd, SHELP_HELP, USAGE_HELP, LHELP_HELP);  // Check
	registerCLI("version", versionCmd, SHELP_VERSION, USAGE_VERSION, LHELP_VERSION); // Check
	registerCLI("set", setCmd, SHELP_SET, USAGE_SET, LHELP_SET); // Check
	registerCLI("get", getCmd, SHELP_GET, USAGE_GET, LHELP_GET); // Check
	registerCLI("source", sourceCmd, SHELP_SOURCE, USAGE_SOURCE, LHELP_SOURCE); // Check
	registerCLI("ifconfig", ifconfigCmd, SHELP_IFCONFIG, USAGE_IFCONFIG, LHELP_IFCONFIG);
	registerCLI("route", routeCmd, SHELP_ROUTE, USAGE_ROUTE, LHELP_ROUTE);
	registerCLI("arp", arpCmd, SHELP_ARP, USAGE_ARP, LHELP_ARP);
	registerCLI("ping", pingCmd, SHELP_PING, USAGE_PING, LHELP_PING); // Check
	registerCLI("console", consoleCmd, SHELP_CONSOLE, USAGE_CONSOLE, LHELP_CONSOLE); // Check
	registerCLI("halt", haltCmd, SHELP_HALT, USAGE_HALT, LHELP_HALT); // Check
	registerCLI("exit", haltCmd, SHELP_EXIT, USAGE_EXIT, LHELP_EXIT); // Check
	registerCLI("queue", queueCmd, SHELP_QUEUE, USAGE_QUEUE, LHELP_QUEUE); // Check
	registerCLI("qdisc", qdiscCmd, SHELP_QDISC, USAGE_QDISC, LHELP_QDISC); // Check
	registerCLI("spolicy", spolicyCmd, SHELP_SPOLICY, USAGE_SPOLICY, LHELP_SPOLICY); // Check
	registerCLI("class", classCmd, SHELP_CLASS, USAGE_CLASS, LHELP_CLASS);
	registerCLI("filter", filterCmd, SHELP_FILTER, USAGE_FILTER, LHELP_FILTER);
        registerCLI("throughput", throughputCmd, SHELP_THROUGHPUT, USAGE_THROUGHPUT, LHELP_THROUGHPUT);
                


	if (rarg->config_dir != NULL)
		chdir(rarg->config_dir);                  // change to the configuration directory
	if (rarg->config_file != NULL)
	{
		FILE *ifile = fopen(rarg->config_file, "r");
		rl_instream = ifile;              // redirect the input stream
		CLIProcessCmds(ifile, 0);
		rl_instream = stdin;
	}

	if (rarg->cli_flag != 0)
		stat = pthread_create((pthread_t *)(&(rarg->clihandler)), NULL, CLIProcessCmdsInteractive, (void *)stdin);

	pthread_join(rarg->clihandler, (void **)&jstat);
	verbose(2, "[cliHandler]:: Destroying the CLI datastructures ");
	CLIDestroy();
}



/*
 * This function is called by the thread that is spawned to handle the CLI.
 * It reads the console and invokes the handlers to process the commands typed
 * by the user at the GINI router.
 */

void *CLIProcessCmdsInteractive(void *arg)
{
	FILE *fp = (FILE *)arg;
	CLIPrintHelpPreamble();
        //OSPFinit();
        CLIProcessCmds(fp, 1);
}



/*
 * managing the signals: first an ignore function.
 */
void dummyFunction(int sign)
{
	printf("Signal [%d] is ignored \n", sign);
}



void parseACLICmd(char *str)
{
	char *token;
	cli_entry_t *clie;
	char orig_str[MAX_TMPBUF_LEN];

	strcpy(orig_str, str);
	token = strtok(str, " \n");
	if ((clie = map_get(cli_map, token)) != NULL)
		clie->handler((void *)clie);
	else
	{
		printf("WARNING: %s not a gRouter command (deferring to Linux)\n", token);
		system(orig_str);
	}
}


void CLIPrintHelpPreamble()
{
	printf("\nGINI Router Shell, version: %s", prog_version());
	printf("\n%s\n", HELP_PREAMPLE);
}


void CLIPrintHelp() {
	cli_entry_t *clie;

	CLIPrintHelpPreamble();
	if (!(cli_mapper = mapper_create(cli_map)))
	{
		map_destroy(&cli_map);
		return;
	}
	while (mapper_has_next(cli_mapper) == 1)
	{

		const Mapping *cli_mapping = mapper_next_mapping(cli_mapper);

		clie = (cli_entry_t *) mapping_value(cli_mapping);
		printf("%s:: \t%s\n\t%s\n", clie->keystr, clie->usagestr,
				clie->short_helpstr);
	}

}


/*
 * Read a string, and return a pointer to it.
 * Returns NULL on EOF.
 */
char *rlGets(int online)
{
	char prompt[MAX_TMPBUF_LEN];

	if (cur_line != NULL)
	{
		free (cur_line);
		cur_line = (char *)NULL;
	}

	sprintf(prompt, "GINI-%s $ ", rconfig.router_name);

	do
	{
		// Get a line from the user.
		cur_line = readline(prompt);
	} while (online && (cur_line == NULL));

	// If the line has any text in it,
	// save it on the history.
	if (cur_line && *cur_line)
		add_history (cur_line);

	return (cur_line);
}



/*
 * process CLI. The file pointer fp already points to an open stream. The
 * boolean variable online indicates whether processCLI is operating with
 * a terminal or from a batch input. For batch input, it should be FALSE.
 */

void CLIProcessCmds(FILE *fp, int online)
{
	int state = PROGRAM;
	char full_line[MAX_BUF_LEN];
	int lineno = 0;
	full_line[0] = '\0';

	// NOTE: the input stream for readline is already redirected
	// when processCLI is called from the "source" command.
	while ((cur_line = rlGets(online)) != NULL)
	{
		switch (state)
		{
		case PROGRAM:
			if (cur_line[0] == CARRIAGE_RETURN)
				break;
			if (cur_line[0] == LINE_FEED)
				break;
			if (cur_line[0] == COMMENT_CHAR)
				state = COMMENT;
			else if ((strlen(cur_line) > 2) && (cur_line[strlen(cur_line)-2] == CONTINUE_CHAR))
			{
				state = JOIN;
				strcat(full_line, cur_line);
			}
			else
			{
				strcat(full_line, cur_line);
				if (strlen(full_line) > 0)
					parseACLICmd(full_line);
				full_line[0] = '\0';
			}
			lineno++;
			break;
		case JOIN:
			full_line[strlen(full_line)-2] = '\0';
			if (cur_line[strlen(cur_line)-2] == CONTINUE_CHAR)
				strcat(full_line, cur_line);
			else
			{
				state = PROGRAM;
				strcat(full_line, cur_line);
				if (strlen(full_line) > 0)
					parseACLICmd(full_line);
				full_line[0] = '\0';
			}
			break;
		case COMMENT:
			if (cur_line[0] != COMMENT_CHAR)
			{
				if (cur_line[strlen(cur_line)-2] == CONTINUE_CHAR)
				{
					state = JOIN;
					strcat(full_line, cur_line);
				} else
				{
					state = PROGRAM;
					strcat(full_line, cur_line);
					if (strlen(full_line) > 0)
						parseACLICmd(full_line);
					full_line[0] = '\0';
				}
			}
			break;
			lineno++;
		}
	}
}




void CLIDestroy()
{
	mapper_destroy(&cli_mapper);
	map_destroy(&cli_map);
}


void registerCLI(char *key, void (*handler)(),
		 char *shelp, char *usage, char *lhelp)
{
	cli_entry_t *clie = (cli_entry_t *) malloc(sizeof(cli_entry_t));

	clie->handler = handler;
	strcpy(clie->long_helpstr, lhelp);
	strcpy(clie->usagestr, usage);
	strcpy(clie->short_helpstr, shelp);
	strcpy(clie->keystr, key);

	verbose(2, "adding command %s.. to cli map ", key);
	map_add(cli_map, key, clie);

}


/*------------------------------------------------------------------
 *               C L I  H A N D L E R S
 *-----------------------------------------------------------------*/


// some macro defintions...
#define GET_NEXT_PARAMETER(X, Y)			if (((next_tok = strtok(NULL, " \n")) == NULL) ||  \
												(strcmp(next_tok, X) != 0)) { error(Y); return; }; \
												next_tok = strtok(NULL, " \n")
#define GET_THIS_PARAMETER(X, Y)           	if (((next_tok = strtok(NULL, " \n")) == NULL) ||  \
												(strstr(next_tok, X) == NULL)) { error(Y); return; }
#define GET_THIS_OR_THIS_PARAMETER(X, Z, Y) if (((next_tok = strtok(NULL, " \n")) == NULL) ||  \
												((strstr(next_tok, X) == NULL) && \
												 (strstr(next_tok, Z) == NULL))) { error(Y); return; }

int getDevType(char *str)
{

	if (strstr(str, "eth") != NULL)
		return ETH_DEV;
	if (strstr(str, "tap") != NULL)
		return TAP_DEV;
}


/*
 * Handler for the interface configuration command:
 * ifconfig add eth1 -socket socketfile -addr IP_addr  -hwaddr MAC [-gateway GW] [-mtu N]
 * ifconfig add tap0 -device dev_location -addr IP_addr -hwaddr MAC
 * ifconfig del eth0|tap0
 * ifconfig show [brief|verbose]
 * ifconfig up eth0|tap0
 * ifconfig down eth0|tap0
 * ifconfig mod eth0 (-gateway GW | -mtu N)
 */
void ifconfigCmd()
{
	char *next_tok;
	interface_t *iface;
	char dev_name[MAX_DNAME_LEN], con_sock[MAX_NAME_LEN], dev_type[MAX_NAME_LEN];
	uchar mac_addr[6], ip_addr[4], gw_addr[4];
	int mtu, interface, mode;

	// set default values for optional parameters
	bzero(gw_addr, 4);
	mtu = DEFAULT_MTU;
	mode = NORMAL_LISTING;

	// we have already matched ifconfig... now parsing rest of the parameters.
	next_tok = strtok(NULL, " \n");

	if (next_tok == NULL)
	{
		printf("[ifconfigCmd]:: missing action parameter.. type help ifconfig for usage.\n");
		return;
	}
	if (!strcmp(next_tok, "add"))
	{
		GET_THIS_OR_THIS_PARAMETER("eth", "tap", "ifconfig:: missing interface spec ..");
		strcpy(dev_name, next_tok);
		sscanf(dev_name, "%[a-z]", dev_type);
		interface = gAtoi(dev_name);

		if ((interface == 0) && (strcmp(dev_type, "eth") == 0))
		{
			printf("[ifconfigCmd]:: device number 0 is reserved for tap - start from 1\n");
			return;
		}

		if (strcmp(dev_type, "eth") == 0)
		{
			GET_NEXT_PARAMETER("-socket", "ifconfig:: missing -socket spec ..");
			strcpy(con_sock, next_tok);
		}

		GET_NEXT_PARAMETER("-addr", "ifconfig:: missing -addr spec ..");
		Dot2IP(next_tok, ip_addr);

		GET_NEXT_PARAMETER("-hwaddr", "ifconfig:: missing -hwaddr spec ..");
		Colon2MAC(next_tok, mac_addr);

		while ((next_tok = strtok(NULL, " \n")) != NULL)
			if (!strcmp("-gateway", next_tok))
			{
				next_tok = strtok(NULL, " \n");
				Dot2IP(next_tok, gw_addr);
			} else if (!strcmp("-mtu", next_tok))
			{
				next_tok = strtok(NULL, " \n");
				mtu = atoi(next_tok);
			}

		if (strcmp(dev_type, "eth") == 0)
			iface = GNETMakeEthInterface(con_sock, dev_name, mac_addr, ip_addr, mtu, 0);
		else
			iface = GNETMakeTapInterface(dev_name, mac_addr, ip_addr);

		if (iface != NULL)
		{
			verbose(2, "[configureInterfaces]:: Inserting the definition in the interface table ");
			GNETInsertInterface(iface);
			addMTUEntry(MTU_tbl, iface->interface_id, iface->device_mtu, iface->ip_addr);
			// for tap0 interface the MTU value cannot be changed. should we allow change?
		}
	}
	else if (!strcmp(next_tok, "del"))
	{
		GET_THIS_OR_THIS_PARAMETER("eth", "tap", "ifconfig:: missing interface spec ..");
		strcpy(dev_name, next_tok);
		interface = gAtoi(next_tok);
		destroyInterfaceByIndex(interface);
		deleteMTUEntry(interface);
	}
	else if (!strcmp(next_tok, "up"))
	{
		GET_THIS_OR_THIS_PARAMETER("eth", "tap", "ifconfig:: missing interface spec ..");
		strcpy(dev_name, next_tok);
		interface = gAtoi(next_tok);
		upInterface(interface);

	}
	else if (!strcmp(next_tok, "down"))
	{
		GET_THIS_OR_THIS_PARAMETER("eth", "tap", "ifconfig:: missing interface spec ..");
		strcpy(dev_name, next_tok);
		interface = gAtoi(next_tok);
		downInterface(interface);
	}
	else if (!strcmp(next_tok, "mod"))
	{
		GET_THIS_PARAMETER("eth", "ifconfig:: missing interface spec ..");
		strcpy(dev_name, next_tok);
		interface = gAtoi(next_tok);

		while ((next_tok = strtok(NULL, " \n")) != NULL)
			if (!strcmp("-gateway", next_tok))
			{
				next_tok = strtok(NULL, " \n");
				strcpy(gw_addr, next_tok);
			} else if (!strcmp("-mtu", next_tok))
			{
				next_tok = strtok(NULL, " \n");
				mtu = atoi(next_tok);
			}

		changeInterfaceMTU(interface, mtu);
	}
	else if (!strcmp(next_tok, "show"))
	{
		if ((next_tok = strtok(NULL, " \n")) != NULL)
		{
			if (strstr(next_tok, "bri") != NULL)
				mode = BRIEF_LISTING;
			else if (strstr(next_tok, "verb") != NULL)
				mode = VERBOSE_LISTING;
		} else
			mode = NORMAL_LISTING;

		printInterfaces(mode);
	}
	return;
}


/*
 * Handler for the connection "route" command
 * route show
 * route add -dev eth0|tap0 -net nw_addr -netmask mask [-gw gw_addr]
 * route del route_number
 */
void routeCmd()
{
	char *next_tok;
	char tmpbuf[MAX_TMPBUF_LEN];
	uchar net_addr[4], net_mask[4], nxth_addr[4];
	int interface, del_route;
	char dev_name[MAX_DNAME_LEN];

	// set defaults for optional parameters
	bzero(nxth_addr, 4);

	next_tok = strtok(NULL, " \n");

	if (next_tok != NULL)
	{
		if (!strcmp(next_tok, "add"))
		{
			GET_NEXT_PARAMETER("-dev", "route:: missing device name ..");
			strcpy(dev_name, next_tok);
			interface = gAtoi(next_tok);

			GET_NEXT_PARAMETER("-net", "route:: missing network address ..");
			Dot2IP(next_tok, net_addr);

			GET_NEXT_PARAMETER("-netmask", "route:: missing netmask ..");
			Dot2IP(next_tok, net_mask);

			verbose(2, "[routeCmd]:: Device %s Interface %d, net_addr %s, netmask %s ",
			       dev_name, interface, IP2Dot(tmpbuf, net_addr), IP2Dot((tmpbuf+20), net_mask));

			if (((next_tok = strtok(NULL, " \n")) != NULL) &&
			    (!strcmp("-gw", next_tok)))
			{
				next_tok = strtok(NULL, " \n");
				Dot2IP(next_tok, nxth_addr);
			}
			addRouteEntry(route_tbl, net_addr, net_mask, nxth_addr, interface);
		}
		else if (!strcmp(next_tok, "del"))
		{
			next_tok = strtok(NULL, " \n");
			del_route = gAtoi(next_tok);
			deleteRouteEntryByIndex(route_tbl, del_route);
		}
		else if (!strcmp(next_tok, "show"))
			printRouteTable(route_tbl);
	}
	return;
}

/*
 * Handler for the connection "arp" command:
 * arp show
 * arp show -ip ip_addr
 * arp del
 * arp del -ip ip_addr
 */
void arpCmd()
{
	char *next_tok;
	uchar mac_addr[6], ip_addr[4];

	next_tok = strtok(NULL, " \n");

	if (next_tok == NULL)
	{
		printf("[arpCmd]:: missing arp action.. type help arp for usage \n");
		return;
	}

	if (!strcmp(next_tok, "show"))
		ARPPrintTable();
	else if (!strcmp(next_tok, "del"))
	{
		if ((next_tok = strtok(NULL, " \n")) != NULL)
		{
			if (!strcmp("-ip", next_tok))
			{
				next_tok = strtok(NULL, " \n");
				Dot2IP(next_tok, ip_addr);
				ARPDeleteEntry(ip_addr);
			}
		} else
			ARPReInitTable();
	} else if (!strcmp(next_tok, "add"))
    {
		if ((next_tok = strtok(NULL, " \n")) != NULL)
		{
			if (!strcmp("-ip", next_tok))
			{
				next_tok = strtok(NULL, " \n");
				Dot2IP(next_tok, ip_addr);
			}
			} else if ((next_tok = strtok(NULL, " \n")) != NULL)
            {
				if (!strcmp("-mac", next_tok))
				{
					next_tok = strtok(NULL, " \n");
					Colon2MAC(next_tok, mac_addr);
					ARPAddEntry(ip_addr, mac_addr);
				}
            }
    }
}



ip_spec_t *parseIPSpec(char *instr)
{
	ip_spec_t *ips;
	char *ipaddr, *preflen;
	char *str2, *ipnum;
	char *saveptr, *saveptr2;
	int i=4;

	ips = (ip_spec_t *) malloc(sizeof(ip_spec_t));
	bzero(ips, sizeof(ip_spec_t));

	ipaddr = strtok_r(instr, " /", &saveptr);
	preflen = strtok_r(NULL, " /", &saveptr);
	if (preflen == NULL)
		ips->preflen = 32;
	else
		ips->preflen = atoi(preflen);
	for (str2 = ipaddr; i--; str2 = NULL)
	{
		ipnum = strtok_r(str2, ".", &saveptr2);
		if (ipnum == NULL) break;

		ips->ip_addr[i] = atoi(ipnum);
	}

	return ips;
}


port_range_t *parsePortRangeSpec(char *instr)
{
	port_range_t *prs;
	char *savestr, *port;

	prs = (port_range_t *) malloc(sizeof(port_range_t));
	bzero(prs, sizeof(port_range_t));

	port = strtok_r(instr, "-", &savestr);
	prs->minport = atoi(port);

	port = strtok_r(NULL, "-", &savestr);
	prs->maxport = atoi(port);

	return prs;
}



/*
 * class add class_name [-src ( packet spec )] [-dst ( packet spec )]
 * class del class_name
 * class show
 * packet_spec = -net ipaddr/prevlen -port lower-upper -prot number
 */
void classCmd()
{
	char *next_tok;
	char tmpbuf[MAX_TMPBUF_LEN];
	char cname[MAX_DNAME_LEN];
	int sside;
	ip_spec_t *ips;
	port_range_t *pps;

	next_tok = strtok(NULL, " \n");
	if (next_tok != NULL)
	{
		if (!strcmp(next_tok, "add"))
		{
			next_tok = strtok(NULL, " \n");
			strcpy(cname, next_tok);
			addClassDef(classifier, cname);

			while ((next_tok = strtok(NULL, " \n")) != NULL)
			{
				if (!strcmp(next_tok, "-src")) sside = 1;
				if (!strcmp(next_tok, "-dst")) sside = 0;

				while ((next_tok = strtok(NULL, "( )\n")) != NULL)
				{
					if (!strcmp(next_tok, "-net"))
					{
						next_tok = strtok(NULL, " )\n");
						ips = parseIPSpec(next_tok);
						insertIPSpec(classifier, cname, sside, ips);
					}
					if (!strcmp(next_tok, "-port"))
					{
						next_tok = strtok(NULL, " )\n");
						pps = parsePortRangeSpec(next_tok);
						insertPortRangeSpec(classifier, cname, sside, pps);
					}
					if (!strcmp(next_tok, "-prot"))
					{
						next_tok = strtok(NULL, " )\n");
						insertProtSpec(classifier, cname, gAtoi(next_tok));
					}
					if (!strcmp(next_tok, "-tos"))
					{
						next_tok = strtok(NULL, " )\n");
						insertTOSSpec(classifier, cname, gAtoi(next_tok));
					}
				}
			}
		}
		else if (!strcmp(next_tok, "del"))
		{
			next_tok = strtok(NULL, " \n");
			if (next_tok != NULL)
			{
				strcpy(cname, next_tok);
				delClassDef(classifier, cname);
			}
		}
		else if (!strcmp(next_tok, "show"))
		  printClassifier(classifier);
	}
	return;
}




/*
 * filter add ( deny | allow ) class_name
 * filter move rule_number (up | down | top | bottom )
 * filter del rule_number
 * filter [on|off]
 * filter show
 * filter stats
 * filter flush
 */
void filterCmd()
{
	char *next_tok;
	char tmpbuf[MAX_TMPBUF_LEN];
	char cname[MAX_DNAME_LEN];
	int type, rulenum;


	next_tok = strtok(NULL, " \n");
	if (next_tok == NULL)
		printf("Filtering is %s\n", filter->filteron? "on" : "off");
	else
	{
		if (!strcmp(next_tok, "on"))
			filter->filteron = 1;
		else if (!strcmp(next_tok, "off"))
			filter->filteron = 0;
		else if (!strcmp(next_tok, "add"))
		{
			GET_THIS_OR_THIS_PARAMETER("deny", "allow", "deny | allow expected ");
			if (!strcmp(next_tok, "deny"))
				type = 0;
			else if (!strcmp(next_tok, "allow"))
				type = 1;
			else
				return;
			if ((next_tok = strtok(NULL, " \n")) != NULL)
				addFilterRule(filter, type, next_tok);
		}
		else if (!strcmp(next_tok, "move"))
		{
			if ((next_tok = strtok(NULL, " \n")) != NULL)
			{
				rulenum = atoi(next_tok);
				if ((rulenum < 0) || (rulenum >= filter->rulecnt))
				{
					printf("Invalid rule number %d \n", rulenum);
					return;
				}
				next_tok = strtok(NULL, " \n");
				moveRule(filter, rulenum, next_tok);
			}
		}
		else if (!strcmp(next_tok, "del"))
		{
			if ((next_tok = strtok(NULL, " \n")) != NULL)
			{
				rulenum = atoi(next_tok);
				if ((rulenum < 0) || (rulenum >= filter->rulecnt))
				{
					printf("Invalid rule number %d \n", rulenum);
					return;
				}
				delFilterRule(filter, rulenum);
			}
		}
		else if (!strcmp(next_tok, "show"))
			printFilter(filter);
		else if (!strcmp(next_tok, "stats"))
			printFilterStats(filter);
		else if (!strcmp(next_tok, "flush"))
			flushFilter(filter);
	}
}

void throughputCmd(){
    printf("Throughput Yo!\n");
    throughputCalc();
}


/*
 * prints the version number of the gRouter.
 * TODO: make this version number globally controlled variable that is
 * set by the installer.
 */
void versionCmd()
{
	printf("\nGINI Router Version: %s \n\n", prog_version());
}



/*
 * halts the gRouter.
 * TODO: should we do any specific clean up of state before halting the router?
 */
void haltCmd()
{
	verbose(1, "[haltCmd]:: Router %s shutting down.. ", prog_name());
	raise(SIGUSR1);
}


/*
 * send a ping packet...
 * ping [-num] IP_addr [-size payload size]
 */

void pingCmd()
{
	char *next_tok = strtok(NULL, " \n");
	int tries, pkt_size;
	uchar ip_addr[4];
	char tmpbuf[MAX_TMPBUF_LEN];

	if (next_tok == NULL)
		return;

	if (next_tok[0] == '-')
	{
		tries = gAtoi(next_tok);
		next_tok = strtok(NULL, " \n");
	} else
		tries = 1;
	Dot2IP(next_tok, ip_addr);
	verbose(2, "[pingCmd]:: ping command sent, tries = %d, IP = %s",
		tries, IP2Dot(tmpbuf, ip_addr));

	if ((next_tok = strtok(NULL, " \n")) != NULL)
	{
		if (!strcmp(next_tok, "-size"))
		{
			next_tok = strtok(NULL, " \n");
			pkt_size = atoi(next_tok);
		} else
			pkt_size = 64;
	} else
		pkt_size = 64;
	ICMPDoPing(ip_addr, pkt_size, tries);
}


/*
 * set verbose [value]
 * set raw-time [true | false ]
 * set update-delay value
 * set sched-cycle value
 */
void setCmd()
{
	char *next_tok = strtok(NULL, " \n");
	int level, cyclelen, rawmode, updateinterval;

	if (next_tok == NULL)
		error("[setCmd]:: ERROR!! missing set-parameter");
	else if (!strcmp(next_tok, "sched-cycle"))
	{
		if ((next_tok = strtok(NULL, " \n")) != NULL)
		{
			cyclelen = atoi(next_tok);
			if (cyclelen >=0)
				rconfig.schedcycle = cyclelen;
			else
				verbose(1, "ERROR!! schedule cycle length should be positive \n");
		} else
			printf("\nSchedule cycle length: %d (microseconds) \n", rconfig.schedcycle);
	} else if (!strcmp(next_tok, "verbose"))
	{
		if ((next_tok = strtok(NULL, " \n")) != NULL)
		{
			level = atoi(next_tok);
			if ((level >= 0) && (level <= 6))
				prog_set_verbosity_level(level);
			else
				verbose(1, "[setCmd]:: ERROR!! level should be in [0..6] \n");
		} else
			printf("\nVerbose level: %ld \n", prog_verbosity_level());
	} else if (!strcmp(next_tok, "raw-times"))
	{
		if ((next_tok = strtok(NULL, " \n")) != NULL)
		{
			rawmode = atoi(next_tok);
			if ((rawmode == 0) || (rawmode == 1))
				setTimeMode(rawmode);
			else
				printf("\nRaw time mode: %d  \n", getTimeMode());
		}
		else if (!strcmp(next_tok, "update-delay"))
		{
			if ((next_tok = strtok(NULL, " \n")) != NULL)
			{
				updateinterval = atoi(next_tok);
				if (updateinterval >=2)
					setUpdateInterval(updateinterval);
				else
					verbose(1, "Invalid update interval.. setting failed.. \n");
			}
			else
				printf("Update interval: %d (seconds) \n", getUpdateInterval());
		}
	}
}



void sourceCmd()
{
	FILE *fp;
	char *next_tok = strtok(NULL, " \n");

	if (next_tok == NULL)
	{
		error("[sourceCmd]:: ERROR!! missing file specification...");
		return;
	}

	if ((fp = fopen(next_tok, "r")) == NULL)
	{
		error("[sourceCmd]:: ERROR!! cannot open file %s.. ", next_tok);
		return;
	}

	rl_instream = fp;
	CLIProcessCmds(fp, 0);
	rl_instream = stdin;
}


void consoleCmd()
{
	char *next_tok = strtok(NULL, " \n");

	if (next_tok == NULL)
		consoleGetState();
	else if (!strcmp(next_tok, "restart"))
		consoleRestart(rconfig.config_dir, rconfig.router_name);
	else
	{
		verbose(2, "[consoleCmd]:: Unknown port action requested \n");
		return;
	}
}


/*
 * helpCmd - this implements the following command line.
 * help - prints a general help usage message
 * help command - prints the usage for the specified command
 * the last usage should also print an error if the command is unknown.
 */

void helpCmd()
{
	char tmpbuf[MAX_TMPBUF_LEN];
	char *next_tok = strtok(NULL, " \n");
	cli_entry_t *n_clie;

	if (next_tok == NULL)
		CLIPrintHelp();
	else
	{
		n_clie = (cli_entry_t *)map_get(cli_map, next_tok);
		if (n_clie == NULL)
			printf("ERROR! No help for command: %s \n", next_tok);
		else
		{
			if (strstr(n_clie->long_helpstr, ".hlp") != NULL)
			{
				sprintf(tmpbuf, "man %s/grouter/helpdefs/%s", getenv("GINI_SHARE"), n_clie->long_helpstr);
				system(tmpbuf);
			} else
			{
				printf("\n%s:: %s\n", n_clie->keystr, n_clie->usagestr);
				printf("%s\n", n_clie->long_helpstr);
			}
		}
	}
}

/*
 * get parameter_name
 */
void getCmd()
{
	char *next_tok = strtok(NULL, " \n");
	int level, cyclelen, rawmode, updateinterval;

	if (next_tok == NULL)
		error("[getCmd]:: ERROR!! missing get-parameter");
	else if (!strcmp(next_tok, "sched-cycle"))
		printf("\nSchedule cycle length: %d (microseconds) \n", rconfig.schedcycle);
	else if (!strcmp(next_tok, "verbose"))
		printf("\nVerbose level: %ld \n", prog_verbosity_level());
	else if (!strcmp(next_tok, "raw-times"))
		printf("\nRaw time mode: %d  \n", getTimeMode());
	else if (!strcmp(next_tok, "update-delay"))
		printf("Update interval: %d (seconds) \n", getUpdateInterval());
}



/*
 * queue add class_name qdisc_name [-size num_slots] [-weight value] [-delay delay_microsec]
 * queue show
 * queue del queue_number
 * queue mod queue_number [-weight value] [-delay delay_microsec]
 * queue stats [queue_number]
 */
void queueCmd()
{
	char *next_tok;
	char cname[MAX_DNAME_LEN], qdisc[MAX_DNAME_LEN];
	// the following parameters are set to default values which are sometimes overwritten
	int num_slots = 0;   // means, set to default
	double weight = 1.0, delay = 2.0;


	if ((next_tok = strtok(NULL, " \n")) != NULL)
	{
		if (!strcmp(next_tok, "add"))
		{
			next_tok = strtok(NULL, " \n");
			strcpy(cname, next_tok);
			if (getClassDef(classifier, cname) == NULL)
			{
				verbose(1, "[queue]:: class name %s not defined ..", cname);
				return;
			}
			next_tok = strtok(NULL, " \n");
			strcpy(qdisc, next_tok);
			if (lookupQDisc(pcore->qdiscs, qdisc) < 0)
			{
				verbose(1, "[queue]:: qdisc %s not defined .. ", qdisc);
				return;
			}

			while ((next_tok = strtok(NULL, " \n")) != NULL)
			{
				if (!strcmp(next_tok, "-size"))
				{
					next_tok = strtok(NULL, " \n");
					num_slots = atoi(next_tok);
				}
				else if (!strcmp(next_tok, "-weight"))
				{
					next_tok = strtok(NULL, " \n");
					weight = atof(next_tok);
				}
				else if (!strcmp(next_tok, "-delay"))
				{
					next_tok = strtok(NULL, " \n");
					delay = atof(next_tok);
				}
			}
			addPktCoreQueue(pcore, cname, qdisc, weight, delay, num_slots);
		}
		else if (!strcmp(next_tok, "show"))
			printAllQueues(pcore);
		else if (!strcmp(next_tok, "del"))
		{
			if ((next_tok = strtok(NULL, " \n")) != NULL)
				delPktCoreQueue(pcore, next_tok);
		}
		else if (!strcmp(next_tok, "mod"))
		{
			if ((next_tok = strtok(NULL, " \n")) != NULL)
			{
				strcpy(cname, next_tok);
				if ((next_tok = strtok(NULL, " \n")) != NULL)
				{
					if (!strcmp(next_tok, "-weight"))
					{
						next_tok = strtok(NULL, " \n");
						weight = atof(next_tok);
						modifyQueueWeight(pcore, cname, weight);
					}
					else if (!strcmp(next_tok, "-qdisc"))
					{
						next_tok = strtok(NULL, " \n");
						modifyQueueDiscipline(pcore, cname, next_tok);
					}
				}
			}
		}
		else if (!strcmp(next_tok, "stats"))
			printQueueStats(pcore);
	}
}



/*
 * qdisc show
 * qdisc add taildrop
 * qdisc add droponfull
 * qdisc add dropfront
 * qdisc add red -min minval -max maxval -pmax pmaxval
 */
void qdiscCmd()
{
	char *next_tok = strtok(NULL, " \n");
	double pmax = 0.9;
	double minval = 0.0, maxval = 1.0;

	if (!strcmp(next_tok, "show"))
		printQdiscs(pcore->qdiscs);
	else if (!strcmp(next_tok, "add"))
	{
		next_tok = strtok(NULL, " \n");
		if (!strcmp(next_tok, "red"))
		{
			while ((next_tok = strtok(NULL, " \n")) != NULL)
			{
				if (!strcmp(next_tok, "-min"))
				{
					next_tok = strtok(NULL, " \n");
					minval = atof(next_tok);
				}
				if (!strcmp(next_tok, "-max"))
				{
					next_tok = strtok(NULL, " \n");
					maxval = atof(next_tok);
				}
				if (!strcmp(next_tok, "-pmax"))
				{
					next_tok = strtok(NULL, " \n");
					pmax = atof(next_tok);
				}
			}
			addRED(pcore->qdiscs, minval, maxval, pmax);
		}
	}
}



/*
 * spolicy show
 */
void spolicyCmd()
{
	char *next_tok = strtok(NULL, " \n");

	if (!strcmp(next_tok, "show"))
		printf("Scheduling policy: rr (round robin)\n");
}
