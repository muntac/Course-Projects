#!/usr/bin/python2

# Revised by Daniel Ng

import sys, os, signal, time, subprocess

from program import Program
#import batch_ipcrm

# set the program names
VS_PROG = "uswitch"
VM_PROG = "glinux"
GR_PROG = "grouter"
GWR_PROG = "gwcenter.sh"
MCONSOLE_PROG = "uml_mconsole"
SOCKET_NAME = "gini_socket"
VS_PROG_BIN = VS_PROG
VM_PROG_BIN = VM_PROG
GR_PROG_BIN = GR_PROG
GWR_PROG_BIN = GWR_PROG
MCONSOLE_PROG_BIN = MCONSOLE_PROG
SRC_FILENAME = "%s/gini_setup" % os.environ["GINI_HOME"] # setup file name
UML_WAIT_DELAY = 0.2 # wait delay between checking alive UML
GROUTER_WAIT = 5 # wait delay between starting routers
GINI_TMP_FILE = ".gini_tmp_file" # tmp file used when checking alive UML
nodes = []

# set this switch True if running gloader without gbuilder
independent = False

def system(command):
    subprocess.call(["/bin/bash", "-c", command])

def popen(command):
    subprocess.Popen(["/bin/bash", "-c", command])

def startGINI(myGINI, options):
    "starting the GINI network components"
    # the starting order is important here
    # first switches, then routers, and at last UMLs.
    print "\nStarting uml switches..."
    success = createVS(myGINI.switches, options.switchDir)
    print "\nStarting Mobiles..."
    success = success and createVMB(myGINI, options)  
    print "\nStarting GINI routers..."
    success = success and createVR(myGINI, options)  
    print "\nStarting UMLs..."
    success = success and createVM(myGINI, options)
    print "\nStarting Wireless access points..."
    success = success and createVWR(myGINI, options)       
    print "\nStarting REALMs..."
    success = success and createVRM(myGINI, options)

    if (not success):
        print "Problem in creating GINI network"
        print "Terminating the partally started network (if any)"
        destroyGINI(myGINI, options)
    return success

def createVS(switches, switchDir):
    "create the switch config file and start the switch"
    # create the main switch directory
    makeDir(switchDir)
    switchCount = 0
 
    for switch in switches:
        print "Starting Switch %s...\t" % switch.name,
        ### ------ config ---------- ###
        # name the config file
        subSwitchDir = "%s%s" % (switchDir, switch.name)
        makeDir(subSwitchDir)
        # move to the switch directory
        oldDir = os.getcwd()
        os.chdir(subSwitchDir)

        ### Write targets file ###
        configFile = "%s/targets.conf" % (subSwitchDir)
        # delete confFile if already exists
        if (os.access(configFile, os.F_OK)):
            os.remove(configFile)
        # create the config file
        configOut = open(configFile, "w")
        for target in switch.targets:
            targetSocket = "%s%s/multiswitch\n" % (getFullyQualifiedDir(switchDir), target)
            configOut.write(targetSocket)
        configOut.close()

        switchFlags  = "-l %s.log " % VS_PROG
        switchFlags += "-p %s.pid " % VS_PROG
        switchFlags += "-s gini_socket.ctl "
        switchFlags += "-t targets.conf "
        switchFlags += "-P %s " % switch.priority
        switchFlags += "-m %s " % switch.mac

        if (switch.hub):
            switchFlags += "--hub "

        command = "screen -d -m -S %s %s %s" % (switch.name, VS_PROG_BIN, switchFlags)

        startOut = open("startit.sh", "w")
        startOut.write(command)
        startOut.close()
        os.chmod("startit.sh",0755)
        popen("./startit.sh")
        os.chdir(oldDir)
        print "[OK]"

    time.sleep(0.5)
        
    return True

def createVWR(myGINI, options):

    oldDir = os.getcwd()
    routerDir = options.routerDir

    for wrouter in myGINI.vwr:
        print "Starting Wireless access point %s...\t" % wrouter.name
        subRouterDir = routerDir + "/" + wrouter.name
        makeDir(subRouterDir)
        configFile = "%s/wrouter.conf" % subRouterDir
        configOut = open(configFile, "w")
        configOut.write("ch set prop mode F\n")
        
        if not independent:        
            canvasIn = open("%s/mobile_data/canvas.data" % routerDir, "r")
            line = canvasIn.readline()
            canvasIn.close()
            x = int(line.split(",")[0]) / 4
            y = int(line.split(",")[1]) / 4
            configOut.write("sys set map size %d %d 0\n" % (x, y))
            wrouterIn = open("%s/mobile_data/%s.data" % (routerDir,wrouter.name), "r")
            line = wrouterIn.readline()
            wrouterIn.close()
            relx = int(line.split(",")[0]) / 4
            rely = int(line.split(",")[1]) / 4

        else:
            configOut.write("sys set map size 100 100 0\n")

        for mobile in myGINI.vmb:
            node = nodes.index(mobile.name) + 1         
            configOut.write("mov set node %d switch off\n" % node)
            if not independent:
                nodeIn = open("%s/mobile_data/%s.data" % (routerDir,mobile.name), "r")
                line = nodeIn.readline()
                nodeIn.close()
                x = int(line.split(",")[0]) / 4
                y = int(line.split(",")[1]) / 4
                configOut.write("mov set node %d location %d %d 0\n" % (node, x - relx, y - rely))
            else:
                configOut.write("mov set node %d location 0 0 0\n" % node)

        for netWIF in wrouter.netIFWireless:
            #netWIF.printMe()
            configOut.write(get_IF_properties(netWIF, len(myGINI.vmb)))        
      
        index = len(nodes)-1
        if nodes and nodes[index].find("UML_") >= 0:
            configOut.write("mov set node %d switch off\n" % (index + 1))
            if not independent:
                configOut.write("mov set node %d location %d %d 0\n" % (index + 1, relx, rely))
            else:
                configOut.write("mov set node %d location 0 0 0\n" % (index + 1))
        configOut.write("echo -ne \"\\033]0;" + wrouter.name + "\\007\"")
        configOut.close()

        ### ------- execute ---------- ###
        # go to the router directory to execute the command
        os.chdir(os.environ["GINI_HOME"]+"/data")        
        cmd = "%s -i 1 -c %s -n %d -d uml_virtual_switch" % (GWR_PROG_BIN, configFile, len(nodes))
        #print "running cmd %s" % cmd
        wrouter.num = wrouter.name.split("Wireless_access_point_")[-1]
        command = "screen -d -m -L -S WAP_%s %s" % (wrouter.num, cmd)
        print "Waiting for Mobiles to finish starting up...\t",
        sys.stdout.flush()
        i = 0        
        ready = False        
        while not ready:
            if i > 5:
                print "There was an error in waiting for the mobiles to start up"
                print "%s may not function properly" % wrouter.name
                break
            time.sleep(2)
            for mobile in myGINI.vmb:
                nwIf = mobile.interfaces[0]
                configFile = "/tmp/%s.sh" % nwIf.mac.upper()
                if os.access(configFile, os.F_OK):
                    ready = False                    
                    break
                else:
                    ready = True
            i += 1                           
        
        scriptOut = open("WAP%s_start.sh" % wrouter.num, "w")
        scriptOut.write(command)
        scriptOut.close()
        os.chmod("WAP%s_start.sh" % wrouter.num, 0777)
        system("./WAP%s_start.sh" % wrouter.num)
        system("screen -d -m -L -S VWAP_%s Wireless_Parser" % wrouter.num)
        os.chdir(oldDir)
        print "[OK]"
    return True
           
def get_IF_properties(netWIF, num_nodes):    
    wcard = netWIF.wireless_card
    #energy = netWIF.energy     not implemented by GWCenter
    mobility = netWIF.mobility
    antenna = netWIF.antenna
    mlayer = netWIF.mac_layer
    
    prop = ""
    prop += "wcard set node 1 freq %f\n" % (float(wcard.freq)/1000000)
    prop += "wcard set node 1 bandwidth %f\n" % (float(wcard.bandwidth)/1000000)
    prop += "wcard set node 1 csthreshold %f\n" % (float(wcard.cs) * 1e8)
    prop += "wcard set node 1 rxthreshold %f\n" % (float(wcard.rx) * 1e8)    
    prop += "wcard set node 1 cpthreshold %s\n" % wcard.cp
    prop += "wcard set node 1 pt %s\n" % wcard.pt
    prop += "wcard set node 1 ptx %s\n" % wcard.ptC
    prop += "wcard set node 1 prx %s\n" % wcard.prC
    prop += "wcard set node 1 pidle %s\n" % wcard.pIdle
    prop += "wcard set node 1 psleep %s\n" % wcard.pSleep    
    prop += "wcard set node 1 poff %s\n" % wcard.pOff 
    prop += "wcard set node 1 modtype %s\n" % wcard.module[0]
    for i in range(num_nodes):
        prop += "ant set node %d height %s\n" % (i+1, antenna.ant_h)
        prop += "ant set node %d gain %s\n" % (i+1, antenna.ant_g)
        prop += "ant set node %d sysloss %s\n" % (i+1, antenna.ant_l)
        prop += "ant set node %d jam %s\n" % (i+1, antenna.jam)
    for i in range(num_nodes):
        prop += "mov set node %d mode %s\n" % (i+1, mobility.mType[0])
        prop += "mov set node %d spd min %s\n" % (i+1, mobility.ranMin)
        prop += "mov set node %d spd max %s\n" % (i+1, mobility.ranMax)
    for i in range(num_nodes):
        if mlayer.macType == "MAC 802.11 DCF":
            prop += "mac set node %d mode D11\n" % (i+1)
        else:
            prop += "mac set node %d mode %s\n" % (i+1, mlayer.macType[0])
            prop += "mac set node %d txprob %s\n" % (i+1, mlayer.trans)   
    return prop   

def createVR(myGINI, options):
    "create router config file, and start the router"
    routerDir = options.routerDir
    switchDir = options.switchDir
    # create the main router directory
    makeDir(routerDir)
    for router in myGINI.vr:
        print "Starting Router %s...\t" % router.name,
        sys.stdout.flush()
        ### ------ config ---------- ###
        # name the config file
        subRouterDir = "%s/%s" % (routerDir, router.name)
        makeDir(subRouterDir)
        configFile = "%s/%s.conf" % (subRouterDir, GR_PROG)  
        # delete confFile if already exists
        if (os.access(configFile, os.F_OK)):
            os.remove(configFile)
        # create the config file
        configOut = open(configFile, "w")
        for nwIf in router.netIF:
            # check whether it is connecting to a switch or router
            socketName = getSocketName(nwIf, router.name, myGINI, options);
            if (socketName == "fail"):
                print "Router %s [interface %s]: Target not found" % \
                      (router.name, nwIf.name)
                return False
            else:
                configOut.write(getVRIFOutLine(nwIf, socketName))
        configOut.write("echo -ne \"\\033]0;" + router.name + "\\007\"")
        configOut.close()
        ### ------- execute ---------- ###
        # go to the router directory to execute the command
        oldDir = os.getcwd()
        os.chdir(subRouterDir)
        command = "screen -d -m -L -S %s %s " % (router.name, GR_PROG_BIN)
        command += "--config=%s.conf " % GR_PROG
        command += "--confpath=" + os.environ["GINI_HOME"] + "/data/" + router.name + " "
        command += "--interactive=1 "
        command += "%s" % router.name
        #print command
        startOut = open("startit.sh", "w")
        startOut.write(command)
        startOut.close()
        os.chmod("startit.sh",0755)
        system("./startit.sh")
        print "[OK]"
        os.chdir(oldDir)
        # Wait after starting router so they have time to create sockets
        time.sleep(GROUTER_WAIT)
    return True

def createVM(myGINI, options):
    "create UML config file, and start the UML"
    makeDir(options.umlDir)
    print myGINI.vm
    for uml in myGINI.vm:
        print "Starting UML %s...\t" % uml.name,
        subUMLDir = "%s/%s" % (options.umlDir, uml.name)
        makeDir(subUMLDir)
        # create command line
        command = createUMLCmdLine(uml)
        ### ---- process the UML interfaces ---- ###
        # it creates one config for each interface in the /tmp/ directory
        # and returns a string to be attached to the UML exec command line        
        for nwIf in uml.interfaces:
            # check whether it is connecting to a switch or router
            socketName = getSocketName(nwIf, uml.name, myGINI, options);
            if (socketName == "fail"):
                print "UML %s [interface %s]: Target not found" % (uml.name, nwIf.name)
                return False
            else:
                # create the config file in /tmp and 
                # return a line to be added in the command
                outLine = getVMIFOutLine(nwIf, socketName, uml.name)
            if (outLine):
                command += "%s " % outLine
            else:
                print "[FAILED]"
                return False
        ### ------- execute ---------- ###
        # go to the UML directory to execute the command
        
        oldDir = os.getcwd()
        os.chdir(subUMLDir)
        startOut = open("startit.sh", "w")
        startOut.write(command)
        startOut.close()
        os.chmod("startit.sh",0755)
        system("./startit.sh")
        print "[OK]"      
  
        os.chdir(oldDir)
    return True

def createVMB(myGINI, options):
    "create UML config file, and start the UML"
    baseDir = os.environ["GINI_HOME"] + "/data/uml_virtual_switch"
    makeDir(baseDir)
    makeDir(options.umlDir)
    oldDir = os.getcwd()
    del nodes[:]    
    for mobile in myGINI.vmb:
        print "Starting virtual Switch for Mobile %s...\t" % mobile.name,
        #print nodes
        nodes.append(mobile.name)
        node = len(nodes)
        subUMLDir = baseDir+"/VS_%d" % node
        makeDir(subUMLDir)
        os.chdir(subUMLDir)
#        vsconf = open("uswitch.conf", "w")
#        vsconf.write("logfile uswitch.log\npidfile uswitch.pid\nsocket gw_socket.ctl\nfork\n")
#        vsconf.close()
        popen("%s -s gw_socket.ctl -l uswitch.log -p uswitch.pid&" % VS_PROG)
        print "[OK]" 
        
        print "Starting Mobile %s...\t" % mobile.name,     
        # create command line
        command = createUMLCmdLine(mobile)
            
        for nwIf in mobile.interfaces:                 
            socketName = subUMLDir + "/gw_socket.ctl"
            # create the config file in /tmp and 
            # return a line to be added in the command
            outLine = getVMIFOutLine(nwIf, socketName, mobile.name)
            if (outLine):
                command += "%s " % outLine
            else:
                print "[FAILED]"
                return False
        ### ------- execute ---------- ###
        # go to the UML directory to execute the command
        makeDir(options.umlDir+"/"+mobile.name)
        os.chdir(options.umlDir+"/"+mobile.name)
        startOut = open("startit.sh", "w")
        startOut.write(command)
        startOut.close()
        os.chmod("startit.sh",0755)
        system("./startit.sh")
        #print command
        print "[OK]"
        os.chdir(oldDir)
    return True

def createVRM(myGINI, options):
    "create REALM config file, and start the REALM"
    makeDir(options.umlDir)
    for realm in myGINI.vrm:
        print "Starting REALM %s...\t" % realm.name,
        subUMLDir = "%s/%s" % (options.umlDir, realm.name)
        makeDir(subUMLDir)
        # create command line
        command = createUMLCmdLine(realm)
        ### ---- process the UML interfaces ---- ###
        # it creates one config for each interface in the /tmp/ directory
        # and returns a string to be attached to the UML exec command line        
        for nwIf in realm.interfaces:
            # check whether it is connecting to a switch or router
            socketName = getSocketName(nwIf, realm.name, myGINI, options);
            if (socketName == "fail"):
                print "REALM %s [interface %s]: Target not found" % (realm.name, nwIf.name)
                return False
            else:
                # create the config file in /tmp and 
                # return a line to be added in the command
                outLine = getVMIFOutLine(nwIf, socketName, realm.name)
            if (outLine):
                command += "%s " % outLine
            else:
                print "[FAILED]"
                return False
        ### ------- execute ---------- ###
        # go to the UML directory to execute the command

            oldDir = os.getcwd()
            # os.chdir(subUMLDir)
            # startOut = open("startit.sh", "w")
            # startOut.write(command)
            # startOut.close()
            # os.chmod("startit.sh",0755)
            # system("./startit.sh")
            # os.chdir(os.environ["GINI_SHARE"]+"/vgini")
            vtap = "screen -d -m -S %s-vtap vtap" % realm.name
            print vtap
            system(vtap)
            vtproxy = "screen -d -m -S %s-vtproxy vtproxy %s %s %s" % (realm.name, nwIf.ip, nwIf.mac, socketName)
            print vtproxy
            system(vtproxy)
            print "[OK]"
            os.chdir(oldDir)
    return True

def makeDir(dirName):
    "create a directory if not exists"
    if (not os.access(dirName, os.F_OK)):
        os.mkdir(dirName, 0755)
    return

# socket name is defined as:
# for switch: switchDir/switchName/SOCKET_NAME.ctl
# for router: routerDir/routerName/SOCKET_NAME_interfaceName.ctl
# the socket names are fully qualified file names
def getSocketName(nwIf, name, myGINI, options):
    "Get the socket name the interface is connecting to"
    waps = myGINI.vwr
    for wap in waps:
        # looking for a match in all waps
        if (wap.name == nwIf.target):
            oldDir = os.getcwd()
            switch_sharing = False
            for i in range(len(nodes)):
                if (nodes[i].find("UML_")+nodes[i].find("REALM_")) >= 0:
                    switch_sharing = True
                    break
            nodes.append(name)
            if switch_sharing:
                newDir = os.environ["GINI_HOME"] + "/data/uml_virtual_switch/VS_%d" % (i+1)
            else:
                newDir = os.environ["GINI_HOME"] + "/data/uml_virtual_switch/VS_%d" % len(nodes)                  
                system("mkdir %s" % newDir)
                os.chdir(newDir)
                configOut = open("uswitch.conf", "w")
                configOut.write("logfile uswitch.log\npidfile uswitch.pid\nsocket gw_socket.ctl\nfork\n")
                configOut.close()
                popen("%s -s %s -l uswitch.log -p uswitch.pid &" % (VS_PROG, newDir+"/gw_socket.ctl"))
                os.chdir(oldDir)
            #else:
                #newDir = os.environ["GINI_HOME"] + "/bin/uml_virtual_switch/VS_%d" % len(nodes)
            return newDir + "/gw_socket.ctl"

    routers = myGINI.vr
    for router in routers:
        # looking for a match in all routers
        if (router.name == nwIf.target):
            for remoteNWIf in router.netIF:
                # router matches, looking for a reverse match
                if (remoteNWIf.target == name):
                    # all match create and return the socketname
                    targetDir = getFullyQualifiedDir(options.routerDir)
                    return "%s/%s/%s_%s.ctl" % \
                           (targetDir, router.name, \
                            SOCKET_NAME, remoteNWIf.name)
            # router matches, but reverse match failed
            return "fail"
    switches = myGINI.switches
    for switch in switches:
        if (switch.name == nwIf.target):
            targetDir = getFullyQualifiedDir(options.switchDir)
            return "%s/%s/%s.ctl" % \
                   (targetDir, switch.name, SOCKET_NAME)
    umls = myGINI.vm
    for uml in umls:
        if (uml.name == nwIf.target):
            # target matching a UML; UML can't create sockets
            # so return value is socket name of the current router
            targetDir = getFullyQualifiedDir(options.routerDir)
            return "%s/%s/%s_%s.ctl" % \
                   (targetDir, name, SOCKET_NAME, nwIf.name)

    realms = myGINI.vrm
    for realm in realms:
        if (realm.name == nwIf.target):
            targetDir = getFullyQualifiedDir(options.routerDir)
            return "%s/%s/%s_%s.ctl" % \
                       (targetDir, name, SOCKET_NAME, nwIf.name)
    return "fail"

def getFullyQualifiedDir(dirName):
    "get a fully qualified name of a directory"
    targetDir = ""
    if (dirName[0] == '/'):
        # absolute path
        targetDir = dirName
    elif (dirName[0] == '.'):
        # relative to the current path
        if (len(dirName) > 1):
            targetDir = "%s/%s" % (os.getcwd(), dirName[1:])
        else:
            # just current path
            targetDir = os.getcwd()
    else:
        # relative path
        targetDir = "%s/%s" % (os.getcwd(), dirName)
    return targetDir

def getVRIFOutLine(nwIf, socketName):
    "convert the router network interface specs into a string"
    if (not os.access(socketName, os.F_OK)):
        socketName = "%s_%s.ctl" % (SOCKET_NAME, nwIf.name)
    outLine = "ifconfig add %s " % nwIf.name
    outLine += "-socket %s " % socketName
    outLine += "-addr %s " % nwIf.ip
    # TODO: address the "no network" attrib in VR IF DTD
    # outLine += "-network %s " % nwIf.network
    outLine += "-hwaddr %s " % nwIf.nic
    # TODO: address the "no gw" attrib in VR IF DTD
    if (nwIf.gw):
        outLine += "-gw %s " % nwIf.gw
    if (nwIf.mtu):
        outLine += "-mtu %s " % mwIf.mtu
    outLine += "\n"
    for route in nwIf.routes:
        outLine += "route add -dev %s " % nwIf.name
        outLine += "-net %s " % route.dest
        outLine += "-netmask %s " % route.netmask
        if (route.nexthop):
            outLine += "-gw %s" % route.nexthop
        outLine += "\n"
    outLine += "display update-delay 2\n"
    return outLine

def getVMIFOutLine(nwIf, socketName, name):
    "convert the UML network interface specs into a string"
    configFile = "%s/tmp/%s.sh" % (os.environ["GINI_HOME"], nwIf.mac.upper())
    # delete confFile if already exists
    # also checks whether somebody else using the same MAC address
    if (os.access(configFile, os.F_OK)):
        if (os.access(configFile, os.W_OK)):
            os.remove(configFile)
        else:
            print "Can not create config file for %s" % nwIf.name
            print "Probably somebody else is using the same ",
            print "MAC address %s" % nwIf.mac
            return None
    # create the config file
    configOut = open(configFile, "w")
    if nwIf.ip:
        configOut.write("ifconfig %s " % nwIf.name)
        configOut.write("%s " % nwIf.ip)
        for route in nwIf.routes:
            if route.dest:
                configOut.write("\nroute add -%s %s " % (route.type, route.dest))
            configOut.write("netmask %s " % route.netmask)
            if route.gw:
                configOut.write("gw %s" % route.gw)
        configOut.write("\n")
    configOut.write("echo -ne \"\\033]0;" + name + "\\007\"")
    configOut.close()
    os.chmod(configFile, 0777)
    bakDir = "%s/tmp/UML_bak" % os.environ["GINI_HOME"]
    if not os.access(bakDir, os.F_OK):
        makeDir(bakDir)
    system("cp %s %s" % (configFile, bakDir))
    # prepare the output line
    outLine = "%s=daemon,%s,unix," % (nwIf.name, nwIf.mac)
    outLine += socketName
    return outLine

def createUMLCmdLine(uml):
    command = "screen -d -m -S %s " % uml.name
    ## uml binary name
    if (uml.kernel):
        command += "%s " % uml.kernel
    else:
        command += "%s " % VM_PROG_BIN
    ## uml ID
    command += "umid=%s " % uml.name
    ## handle the file system option
    # construct the cow file name
    fileSystemName = getBaseName(uml.fileSystem.name)
    fsCOWName = os.environ["GINI_HOME"] + "/data/" + uml.name + "/" + fileSystemName + ".cow"
    if (uml.fileSystem.type.lower() == "cow"):
        command += "ubd0=%s,%s " % (fsCOWName, os.environ["GINI_SHARE"] + "/filesystem/" + fileSystemName)
    else:
        command += "ubd0=%s " % uml.fileSystem.name
    ## handle the mem option
    if (uml.mem):
        command += "mem=%s " % uml.mem
    ## handle the boot option
    if (uml.boot):
        command += "con0=%s " % uml.boot
    command += "hostfs=%s " % os.environ["GINI_HOME"]
    return command

def getBaseName(pathName):
    "Extract the filename from the full path"
    pathParts = pathName.split("/")
    return pathParts[len(pathParts)-1]

def destroyGINI(myGINI, options):
    result = True
    print "\nTerminating switches..."
    try:
        result = destroyVS(myGINI.switches, options.switchDir)
    except:
        pass
    print "\nTerminating routers..."
    try:
        result = result and destroyVR(myGINI.vr, options.routerDir)
    except:
        pass
    
    time.sleep(5)
    print "\nTerminating wireless access points..."   
    try:
        result = result and destroyVWR(myGINI.vwr, options.routerDir)
    except:
        pass    

    print "\nTerminating Mobiles..."
    try:
        result = result and  destroyVM(myGINI.vmb, options.umlDir, 1)
    except:
        pass

    print "\nTerminating REALMs..."
    try:
        result = result and  destroyRVM(myGINI.vrm, options.umlDir)
    except:
        pass

    print "\nTerminating UMLs..."
    try:
        result = result and  destroyVM(myGINI.vm, options.umlDir, 0)
    except:
        pass

    #system("killall uswitch screen")

    print "\nCleaning the interprocess message queues"
    #batch_ipcrm.clean_ipc_queues()
    return result

def destroyVS(switches, switchDir):
    for switch in switches:
        print "Stopping Switch %s..." % switch.name
        # get the pid file
        print "\tCleaning the PID file...\t",
        subSwitchDir = "%s/%s" % (switchDir, switch.name)
        pidFile = "%s/%s.pid" % (subSwitchDir, VS_PROG)
        # check the validity of the pid file
        pidFileFound = True
        if (os.access(pidFile, os.R_OK)):
            # kill the switch
            switchPID = getPIDFromFile(pidFile)
            os.kill(switchPID, signal.SIGTERM)
            print "[OK]"
        else:
            pidFileFound = False
            print "[FAILED]"

        if (pidFileFound):
            print "\tStopping Switch %s...\t[OK]" % switch.name
        else:
            print "\tStopping Switch %s...\t[FAILED]" % switch.name
            print "\tKill the switch %s manually" % switch.name
    return True

def destroyVWR(wrouters, routerDir):
    for wrouter in wrouters:
        print "Stopping Router %s..." % wrouter.name
        subRouterDir = "%s/%s" % (routerDir, wrouter.name)
        wrouter.num = wrouter.name.split("Wireless_access_point_")[-1]        
        system("screen -S %s -X eval quit" % ("WAP_%s" % wrouter.num))
        system("screen -S %s -X eval quit" % ("VWAP_%s" % wrouter.num))
        print "\tCleaning the directory...\t",
        try:
            os.remove(subRouterDir+"/wrouter.conf")            
            #os.remove(subRouterDir+"/startit.sh")
            while os.access(subRouterDir+"/wrouter.conf", os.F_OK):
                pass            
            os.rmdir(subRouterDir)
            print "[OK]"        
        except:
            
            print "failed"
            return False    
    
        oldDir = os.getcwd()
        switchDir = "%s/data/uml_virtual_switch" % os.environ["GINI_HOME"]
        os.chdir(switchDir)
        for filename in os.listdir(switchDir):
            pidfile = filename+"/uswitch.pid"            
            if os.access(pidfile, os.F_OK):
                pidIn = open(pidfile)
                line = pidIn.readline().strip()
                pidIn.close()
                os.kill(int(line), signal.SIGTERM)
            while os.access("gw_socket.ctl", os.F_OK):
                time.sleep(0.5)
            system("rm -rf %s" % filename)

        os.chdir(oldDir)        
    return True    
        
def destroyVR(routers, routerDir):
    for router in routers:
        print "Stopping Router %s..." % router.name
        # get the pid file
        print "\tCleaning the PID file...\t",
        subRouterDir = "%s/%s" % (routerDir, router.name)
        pidFile = "%s/%s.pid" % (subRouterDir, router.name)
        # check the validity of the pid file
        pidFileFound = True
        if (os.access(pidFile, os.R_OK)):
            # kill the router
            # routerPID = getPIDFromFile(pidFile)
            # os.kill(routerPID, signal.SIGTERM)
            command = "screen -S %s -X quit" % router.name
            system(command)
        else:
            pidFileFound = False
            print "[FAILED]"
        # clean up the files in the directory
        print "\tCleaning the directory...\t",
        if (os.access(subRouterDir, os.F_OK)):
            for file in os.listdir(subRouterDir):
                fileName = "%s/%s" % (subRouterDir, file)
                if (os.access(fileName, os.W_OK)):
                    os.remove(fileName)
                else:
                    print "\n\tRouter %s: Could not delete file %s" % (router.name, fileName)
                    print "\tCheck your directory"
                    return False
            if (os.access(subRouterDir, os.W_OK)):
                os.rmdir(subRouterDir)
            else:
                print "\n\tRouter %s: Could not remove router directory" % router.name
                print "\tCheck your directory"
                return False
        print "[OK]"
        if (pidFileFound):
            print "\tStopping Router %s...\t[OK]" % router.name
        else:
            print "\tStopping Router %s...\t[FAILED]" % router.name
            print "\tKill the router %s manually" % router.name
    return True

def getPIDFromFile(fileName):
    fileIn = open(fileName)
    lines = fileIn.readlines()
    fileIn.close()
    return int(lines[0].strip())

def destroyRVM(umls,umlDir):
    for uml in umls:
        print "\tStopping REALM %s...\t[OK]" % uml.name
        system("screen -S " + uml.name + "-vtap -p 0 -X stuff \"quitt\n\"")
        system("screen -S " + uml.name + "-vtproxy -X quit")
        print "\tCleaning the directory...\t",
        subUMLDir = "%s/%s" % (umlDir, uml.name)

        # TODO: Determine if we need any data in this folder.
        if (os.access(subUMLDir, os.F_OK)):
            for file in os.listdir(subUMLDir):
                fileName = "%s/%s" % (subUMLDir, file)
                if (os.access(fileName, os.W_OK)):
                    os.remove(fileName)
                else:
                    print "\n\tCould not delete file %s" % (uml.name, fileName)
                    print "\tCheck your directory"
                    return False
            if (os.access(subUMLDir, os.W_OK)):
                 os.rmdir(subUMLDir)
        print "[OK]"
        for nwIf in uml.interfaces:
            configFile = "%s/tmp/%s.sh" % (os.environ["GINI_HOME"],nwIf.mac.upper())
            if (os.access(configFile, os.W_OK)):
                os.remove(configFile)
    return True

def destroyVM(umls, umlDir, mode):
    for uml in umls:
        if mode == 0:        
            print "Stopping UML %s..." % uml.name
        elif mode == 2:
            print "Stopping REALM %s..." % uml.name
            # system("screen -S " + uml.name + "-vtap -p 0 -X stuff \"quitt\n\"")
            # system("screen -S " + uml.name + "-vtap -X quit")
            # system("screen -S " + uml.name + "-vtproxy -X quit")
        else:
            print "Stopping Mobile %s..." % uml.name
        command = "%s %s cad > /dev/null 2>&1" % (MCONSOLE_PROG_BIN, uml.name)      
        system(command)
        # clean up the directory
        print "\tCleaning the directory...\t",
        subUMLDir = "%s/%s" % (umlDir, uml.name)
        if (os.access(subUMLDir, os.F_OK)):
            for file in os.listdir(subUMLDir):
                fileName = "%s/%s" % (subUMLDir, file)
                if (fileName[len(fileName)-4:] != ".cow"):
                    # don't delete the .cow files
                    if (os.access(fileName, os.W_OK)):
                        os.remove(fileName)
                    else:
                        print "\n\tCould not delete file %s" % (uml.name, fileName)
                        print "\tCheck your directory"
                        return False
            ### we are not deleting the subdirecotry as we are
            ### not deleting the .cow files
            #if (os.access(subUMLDir, os.W_OK)):
                # os.rmdir(subUMLDir)
        print "[OK]"
        # delete the config files in the /tmp
        for nwIf in uml.interfaces:
            configFile = "%s/tmp/%s.sh" % (os.environ["GINI_HOME"],nwIf.mac.upper())
            if (os.access(configFile, os.W_OK)):
                os.remove(configFile)
        if mode == 0:
            print "\tStopping UML %s...\t[OK]" % uml.name
        # elif mode == 2:
        #     print "\tStopping REALM %s...\t[OK]" % uml.name
        else:
            print "\tStopping Mobile %s...\t[OK]" % uml.name
            
    # wait until all UMLs are terminated

    if umls:
        if mode == 0:    
            print "Waiting for UMLs to shutdown...",
            sys.stdout.flush()

            while checkProcAlive(VM_PROG_BIN):
                time.sleep(UML_WAIT_DELAY)

        # elif mode == 2:
        #     print "Waiting for REALMs to shutdown...",
        #     sys.stdout.flush()

        #     while checkProcAlive(VM_PROG_BIN):
        #         time.sleep(UML_WAIT_DELAY)
        else:
            print "Waiting for Mobiles to shutdown...",
        print "[OK]"
    return True

def checkProcAlive(procName):
    alive = False
    # grep the UML processes
    system("pidof -x %s > %s" % (procName, GINI_TMP_FILE))
    inFile = open(GINI_TMP_FILE)
    line = inFile.readline()
    inFile.close()
    if not line:
        return False
    else:
        procs = line.split() 

    for proc in procs:
        system("ps aux | grep %s > %s" % (proc, GINI_TMP_FILE))
        inFile = open(GINI_TMP_FILE)
        lines = inFile.readlines()
        inFile.close()
        for line in lines:
            if line.find("grep") >= 0:
                continue
            if line.find(os.getenv("USER")) >= 0:
                return True
        
    os.remove(GINI_TMP_FILE)
    return alive

def writeSrcFile(options):
    "write the configuration in the setup file"
    outFile = open(SRC_FILENAME, "w")
    outFile.write("%s\n" % options.xmlFile)
    outFile.write("%s\n" % options.switchDir)
    outFile.write("%s\n" % options.routerDir)
    outFile.write("%s\n" % options.umlDir)
    outFile.write("%s\n" % options.binDir)
    outFile.close()

def deleteSrcFile():
    "delete the setup file"
    if (os.access(SRC_FILENAME, os.W_OK)):
        os.remove(SRC_FILENAME)
    else:
        print "Could not delete the GINI setup file"

def checkAliveGini():
    "check any of the gini components already running"
    result = False
    if checkProcAlive(VS_PROG_BIN):
        print "At least one of %s is alive" % VS_PROG_BIN
        result = True
    if checkProcAlive(VM_PROG_BIN):
        print "At least one of %s is alive" % VM_PROG_BIN
        result = True
    if checkProcAlive(GR_PROG_BIN):
        print "At least one of %s is alive" % GR_PROG_BIN
        result = True
    return result


#### -------------- MAIN start ----------------####

# create the program processor. This
# 1. accepts and process the command line options
# 2. creates XML processing engine, that in turn
#    a) validates the XML file
#    b) extracts the DOM object tree
#    c) populates the GINI network class library
#    d) performs some semantic/syntax checkings on
#       the extracted specification
myProg = Program(sys.argv[0], SRC_FILENAME)
if (not myProg.processOptions(sys.argv[1:])):
    sys.exit(1)
options = myProg.options

# set the UML directory if is not given via -u option
if (not options.umlDir):
    # set it to the directory pointed by the $UML_DIR env
    # if no such env variable, set the uml dir to curr dir
    if (os.environ.has_key("UML_DIR")):
        options.umlDir = os.environ["UML_DIR"]
    else:
        options.umlDir = "."

# get the binaries
binDir = options.binDir
if (binDir):
    # get the absolute path for binary directory
    if (binDir[len(binDir)-1] == "/"):
        binDir = binDir[:len(binDir)-1]
    binDir = getFullyQualifiedDir(binDir)
    # assign binary names
    # if the programs are not in the specified binDir
    # they will be assumed to be in the $PATH env.
    VS_PROG_BIN = "%s/%s" % (binDir, VS_PROG)
    if (not os.access(VS_PROG_BIN, os.X_OK)):
        VS_PROG_BIN = VS_PROG
    VM_PROG_BIN = "%s/%s" % (binDir, VM_PROG)
    if (not os.access(VM_PROG_BIN, os.X_OK)):
        VM_PROG_BIN = VM_PROG
    GR_PROG_BIN = "%s/%s" % (binDir, GR_PROG)
    if (not os.access(GR_PROG_BIN, os.X_OK)):
        GR_PROG_BIN = GR_PROG
    MCONSOLE_PROG_BIN = "%s/%s" % (binDir, MCONSOLE_PROG)
    if (not os.access(MCONSOLE_PROG_BIN, os.X_OK)):
        MCONSOLE_PROG_BIN = MCONSOLE_PROG

# get the populated GINI network class
# its structure is the same as the XML specification
myGINI = myProg.giniNW

# create or terminate GINI network
print ""
if (myProg.destroyOpt):
    # terminate the current running specification
    print "Terminating GINI network..."
    success = destroyGINI(myGINI, options)
    if (success):
        print "\nGINI network is terminated!!\n"  
    else:
        print "\nThere are errors in GINI network termination\n"
        sys.exit(1)
else:
    # create the GINI instance
    if (not options.keepOld):
        # fail if a GINI already alive
        if checkAliveGini():
            print "\nA Gini is already running"
            print "Terminate it before running another one\n"
            sys.exit(1)

    if not os.environ.has_key("GINI_HOME"):
        print "environment variable $GINI_HOME not set, please set it for GINI to run properly"
        sys.exit(1)

    # create network with current specifcation
    print "Creating GINI network..."
    success = startGINI(myGINI, options)
    writeSrcFile(options)
    if (success):
        print "\nGINI network up and running!!\n"
    else:
        print "\nGINI network start failed!!\n"
