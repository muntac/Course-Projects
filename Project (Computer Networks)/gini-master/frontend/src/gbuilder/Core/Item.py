"""The logical object of a node or edge within a topology"""

from PyQt4 import QtCore

# receive thing
realMnumber=2
alist={'m1ip':'1','m1name':'1','m1mac':'1','m1port':'1','m2ip':'2','m2name':'2','m2mac':'2','m2port':'2'}
# The list of device types and their current index numbers
hostTypes = {'REALM':0, "UML":0, "UML_FreeDOS":0, "UML_Android":0, "Mobile":0}
netTypes = {"Switch":0, "Subnet":0, "Router":0, "Wireless_access_point":0, "Firewall":0}
customTypes = {"Custom":0}
nodeTypes = {"REALM":hostTypes, "UML":hostTypes, "UML_FreeDOS":hostTypes, "UML_Android":hostTypes, "Mobile":hostTypes, "Switch":netTypes, "Subnet":netTypes, "Router":netTypes,
             "Wireless_access_point":netTypes, "Firewall":netTypes, "Custom":customTypes}

commonTypes = ["UML", "Subnet", "Switch", "Router"]
unimplementedTypes = ["UML_FreeDOS", "UML_Android", "Firewall"]

class Item(object):
    def getName(self):
        """
        Return the name of the item.
        """
        return str(self.getProperty("Name"))

    def getID(self):
        """
        Return the index number of the item.
        """
        return int(self.getName().split("_")[-1])

    def getProperties(self):
        """
        Return the properties of the item.
        """
        return self.properties

    def getProperty(self, propName):
        """
        Return the specified property of the item.
        """
        return self.properties[QtCore.QString(propName)]

    def setProperty(self, prop, value):
        """
        Set the specified property of the item.
        """
        self.properties[QtCore.QString(prop)] = QtCore.QString(value)
