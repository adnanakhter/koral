package de.uni_koblenz.west.cidre.slave.networkManager;

import de.uni_koblenz.west.cidre.common.config.impl.Configuration;
import de.uni_koblenz.west.cidre.common.messages.MessageType;
import de.uni_koblenz.west.cidre.common.messages.MessageUtils;
import de.uni_koblenz.west.cidre.common.networManager.NetworkManager;
import de.uni_koblenz.west.cidre.slave.CidreSlave;

import java.nio.ByteBuffer;

/**
 * Implementation of network manager methods specific for {@link CidreSlave}.
 * 
 * @author Daniel Janke &lt;danijankATuni-koblenz.de&gt;
 *
 */
public class SlaveNetworkManager extends NetworkManager {

  public SlaveNetworkManager(Configuration conf, String[] currentServer) {
    super(conf, currentServer);
  }

  public void sendFinish(int clientID) {
    byte[] message = ByteBuffer.allocate(Byte.BYTES + Short.BYTES)
            .put(MessageType.GRAPH_LOADING_COMPLETE.getValue()).putShort((short) clientID).array();
    send(0, message);
  }

  public void sendFailNotification(int slaveID, String message) {
    byte[] messageBytes = MessageUtils.createStringMessage(MessageType.GRAPH_LOADING_FAILED,
            "Graph loading failed on slave " + slaveID + ". Cause: " + message, null);
    byte[] messageB = ByteBuffer.allocate((Byte.BYTES + Short.BYTES + messageBytes.length) - 1)
            .put(messageBytes[0]).putShort((short) slaveID)
            .put(messageBytes, 1, messageBytes.length - 1).array();
    send(0, messageB);
  }

}
