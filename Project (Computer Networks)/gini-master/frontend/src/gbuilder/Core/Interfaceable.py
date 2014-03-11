"""A device that can have interfaces"""

from Device import *

class Interfaceable(Device):
    def __init__(self):
        """
        Create a device that can have interfaces.
        """
        Device.__init__(self)

        self.menu.addAction("Restart", self.restart)
        self.menu.addAction("Stop", self.terminate)

        self.adjacentRouterList = []
        self.adjacentSubnetList = []

        self.con_int = {} # the connection and interface pair

    def generateToolTip(self):
        """
        Add IP address(es) to the tool tip for easier lookup.
        """
        tooltip = self.getName()
        for interface in self.getInterfaces():
            tooltip += "\n\nTarget: " + interface[QtCore.QString("target")].getName() + "\n"
            tooltip += "IP: " + interface[QtCore.QString("ipv4")]          
        self.setToolTip(tooltip)
        
    def attach(self):
        """
        Attach to corresponding device on backend.
        """

        base = "ssh -t " + options["username"] + "@" + options["server"]
        
        screen = " screen -r "
        if self.device_type == "Wireless_access_point":
            screen += "WAP_%d" % self.getID()
        else:
            name = self.getName()
            pid = mainWidgets["tm"].getPID(name)
            if not pid:
                return
            screen += pid + "." + name
            
        command = ""
        
        window_name = str(self.getProperty("name"))  # the strcast is necessary for cloning
        if(self.getName() != window_name):
            window_name += " (" + self.getName() + ")"
        if environ["os"] == "Windows":
            
            startpath = environ["tmp"] + self.getName() + ".start"
            try:
                outfile = open(startpath, "w")
                outfile.write(screen)
                outfile.close()
            except:
                mainWidgets["log"].append("Failed to write to start file!")
                return
            
            command += "putty -"
            if options["session"]:
                command += "load " + options["session"] + " -l " + options["username"] + " -t"
            else:
                command += base
            command += " -m \"" + startpath + "\""
        else:
            command += "xterm -T \"" + window_name + "\" -e \"" + base + screen + "\""

        self.shell = subprocess.Popen(str(command), shell=True)#ShellStarter(command)
        #self.shell.start()


    def addInterface(self, node):
        """
        Add an interface to the list of interfaces with node as target.
        """
        for interface in self.interfaces:
            if interface[QtCore.QString("target")] == node:
                return         
        
        self.interfaces.append({
            QtCore.QString("target"):node,
            QtCore.QString("ipv4"):QtCore.QString(""),
            QtCore.QString("mac"):QtCore.QString(""),
            QtCore.QString("routing"):[]})

    def removeInterface(self, node):
        """
        Remove the interface from the list of interfaces where node is the target.
        """
        interface = None
        for interface in self.interfaces:
            if interface[QtCore.QString("target")] == node:
                break
            interface = None

        if interface:
            self.interfaces.remove(interface)
        
    def getInterfaces(self):
        """
        Return the list of interfaces.
        """
        return self.interfaces

    def getInterface(self, node=None, subnet=None):
        """
        Return an interface from the list of interfaces specified by node or subnet.
        """
        if not node and not subnet:
            return self.interfaces[0]
        elif subnet:
            for interface in self.interfaces:
                if interface[QtCore.QString("subnet")] == subnet:
                    return interface
        else:
            for interface in self.interfaces:
                if interface[QtCore.QString("target")] == node:
                    return interface
            
    def getInterfaceProperty(self, propName, node=None, subnet=None, index=0):
        """
        Return an interface property specified by node or subnet.
        """
        if not node and not subnet:
            return self.interfaces[index][QtCore.QString(propName)]
        interface = self.getInterface(node, subnet)
        if interface:
            return interface[QtCore.QString(propName)]
         
    def setInterfaceProperty(self, prop, value, node=None, subnet=None, index=0):
        """
        Set an interface property specified by node or subnet.
        """
        if not node and not subnet:
            self.interfaces[index][QtCore.QString(prop)] = QtCore.QString(value)
        else:
            interface = self.getInterface(node, subnet)
            if not interface:
                return
            interface[QtCore.QString(prop)] = QtCore.QString(value)
        
        if prop == "ipv4":
            self.generateToolTip()

    def getTable(self, node=None):
        """
        Return the route table from the interface specified by node.
        """
        return self.getInterfaceProperty("routing", node)

    def getEntry(self, subnet, target):
        """
        Return an entry from the route table specified by subnet and target.
        """
        table = self.getInterfaceProperty("routing", target)
        for entry in table:
            if entry[QtCore.QString("subnet")] == subnet:
                return entry

    def getEntryProperty(self, prop, subnet, target):
        """
        Return a property from the entry specified by subnet and target.
        """
        entry = self.getEntry(subnet, target)
        return entry[QtCore.QString(prop)]

    def setEntryProperty(self, prop, value, subnet, target):
        """
        Set a property from the entry specified by subnet and target.
        """
        entry = self.getEntry(subnet, target)
        entry[QtCore.QString(prop)] = value

    def addEntry(self, mask, gateway, subnet, target):
        """
        Add an entry to the table specified by subnet and target.
        """
        entry = {QtCore.QString("netmask"):mask, QtCore.QString("gw"):gateway, QtCore.QString("subnet"):subnet}
        table = self.getTable(target)
        table.append(entry)

    def removeEntry(self, entry, target):
        """
        Remove an entry from the table specified by subnet and target.
        """
        table = self.getTable(target)
        table.remove(entry)
        
    def addAdjacentRouter(self, router, interface):
        """
        Add a router to the list of adjacent ones for route computations.
        """
        self.adjacentRouterList.append([router, interface])

    def getAdjacentRouters(self):
        """
        Return the list of adjacent routers.
        """
        return self.adjacentRouterList

    def addAdjacentSubnet(self, subnet):
        """
        Add a subnet to the list of adjacent ones for route computations.
        """
        self.adjacentSubnetList.append(subnet)

    def getAdjacentSubnets(self):
        """
        Return the list of adjacent subnets.
        """
        return self.adjacentSubnetList

    def emptyAdjacentLists(self):
        """
        Clear the list of adjacent routers and subnets.
        """
        self.adjacentRouterList = []
        self.adjacentSubnetList = []

    def emptyRouteTable(self):
        """
        Clear the route table.
        """
        for interface in self.interfaces:
            interface[QtCore.QString("routing")] = []

    def hasSubnet(self, subnet):
        """
        Check if the specified subnet is in the adjacent list.
        """
        for sub in self.adjacentSubnetList:
            if sub == subnet:
                return True
        return False
    
    def searchSubnet(self, subnet):
        """
        Search the specified subnet in the whole network.
        """
        routerList=self.adjacentRouterList[:]
        
        # Save all found routers in the list, so that we don't visit a router twice
        foundList=[]
        for r in routerList:
            foundList.append(r[0])
            
        while len(routerList) > 0:
            theOne = routerList.pop(0)
            if theOne[0].hasSubnet(subnet):
                return (theOne[0], theOne[1])
            else:
                # Add its adjacent router list to the list
                for router, interface in theOne[0].getAdjacentRouters():
                    # Check if the router is already visited or is in the to be visited list
                    if not router in foundList: 
                        newOne = [router, theOne[1]]
                        routerList.append(newOne)
                    foundList.append(router)
        return (None, None)

    def addRoutingEntry(self, subnet):
        """
        Add an entry to the route table.
        """
        if not self.hasSubnet(subnet):
            device, interface = self.searchSubnet(subnet)
            if interface:
                target = interface[QtCore.QString("target")]
                if interface[QtCore.QString("subnet")] == subnet \
                    and self.device_type == "UML" or self.device_type == "REALM":
                    self.addEntry(interface[QtCore.QString("mask")],
                                  "",
                                  " ",
                                  target)
                elif interface[QtCore.QString("subnet")] == subnet \
                    and self.device_type == "REALM":
                    self.addEntry(interface[QtCore.QString("mask")],
                                  "",
                                  " ",
                                  target)
                else:
                    if target.device_type == "Switch":
                        # interfaceable = target.getTarget(self)
                        # gateway = interfaceable.getInterface(target)[QtCore.QString("ipv4")]
                        gateway = target.getGateway()
                    else:
                        gateway = target.getInterface(self)[QtCore.QString("ipv4")]
                    self.addEntry(interface[QtCore.QString("mask")],
                                  gateway,
                                  subnet,
                                  target)
        else:
            if self.device_type == "Router":
                interface = self.getInterface(None, subnet)
                self.addEntry(interface[QtCore.QString("mask")],
                              "0.0.0.0",
                              subnet,
                              interface[QtCore.QString("target")])

    def toString(self):
        """
        Reimplemented to provide route information.
        """
        devInfo = Device.toString(self)
        interfaceInfo = ""
        for interface in self.interfaces:
            if interface.has_key(QtCore.QString("target")):
                interfaceInfo += "\t\tinterface:" + interface[QtCore.QString("target")].getName() + "\n"
            else:
                interfaceInfo += "\t\twireless interface:\n"
            for prop, value in interface.iteritems():
                if prop == "target":
                    pass
                elif prop == "routing":
                    for route in value:
                        interfaceInfo += "\t\t\t\troute:" + route[QtCore.QString("subnet")] + "\n"
                        for pr, val in route.iteritems():
                            if pr != "subnet":
                                interfaceInfo += "\t\t\t\t\t" + pr + ":" + val + "\n"
                else:
                    interfaceInfo += "\t\t\t" + prop + ":" + value + "\n"
        return devInfo + interfaceInfo
