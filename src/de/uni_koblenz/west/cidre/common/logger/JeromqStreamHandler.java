package de.uni_koblenz.west.cidre.common.logger;

import java.util.logging.Formatter;
import java.util.logging.Handler;
import java.util.logging.LogRecord;

import org.zeromq.ZContext;
import org.zeromq.ZMQ;
import org.zeromq.ZMQ.Socket;

import de.uni_koblenz.west.cidre.common.config.impl.Configuration;
import de.uni_koblenz.west.cidre.common.logger.receiver.JeromqLoggerReceiver;
import de.uni_koblenz.west.cidre.common.networManager.NetworkContextFactory;

/**
 * Sends log messages to {@link JeromqLoggerReceiver}.
 * 
 * @author Daniel Janke &lt;danijankATuni-koblenz.de&gt;
 *
 */
public class JeromqStreamHandler extends Handler {

	public static String DEFAULT_PORT = "4712";

	private final Formatter formatter;

	private final ZContext context;

	private final Socket socket;

	public JeromqStreamHandler(Configuration conf, String[] currentServer,
			String receiver) {
		super();
		if (!receiver.contains(":")) {
			receiver += ":" + DEFAULT_PORT;
		}
		context = NetworkContextFactory.getNetworkContext();
		socket = context.createSocket(ZMQ.PUSH);
		// socket.setDelayAttachOnConnect(true);
		// socket.setSendTimeOut(3000);
		socket.connect("tcp://" + receiver);
		formatter = new CSVFormatter(currentServer);
		send(formatter.getHead(this));
	}

	@Override
	public void publish(LogRecord record) {
		send(formatter.format(record));
	}

	@Override
	public void flush() {
	}

	@Override
	public void close() throws SecurityException {
		send(formatter.getTail(this));
		context.destroySocket(socket);
		NetworkContextFactory.destroyNetworkContext(context);
	}

	private void send(String message) {
		if (message != null && !message.isEmpty()) {
			socket.send(message);
		}
	}

}
