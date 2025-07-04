package org.jnode.nntp;

import jnode.event.IEvent;
import jnode.logger.Logger;
import jnode.module.JnodeModule;
import jnode.module.JnodeModuleException;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * <a href="http://tools.ietf.org/html/rfc2980">RFC 2980 - Common NNTP
 * Extensions</a><br>
 * <a href="http://tools.ietf.org/html/rfc3977">RFC 3977 - Network News Transfer
 * Protocol (NNTP)</a><br>
 * <a href="http://tools.ietf.org/html/rfc6048">RFC 6048 - Network News Transfer
 * Protocol (NNTP) Additions to LIST Command</a><br>
 */
public class NntpModule extends JnodeModule {

	private static final Logger logger = Logger.getLogger(NntpModule.class);
	private ExecutorService executor;
	private ServerSocket serverSocket;

	private static final String DEFAULT_PORT = "1119";
	private static final String PORT_PROPERTY = "nntp.port";
	private static final int THREAD_POOL_SIZE = 50;

	public NntpModule(String configFile) throws JnodeModuleException {
		super(configFile);
		this.executor = Executors.newFixedThreadPool(THREAD_POOL_SIZE);
	}

	@Override
	public void start() {
		String port = properties.getProperty(PORT_PROPERTY, DEFAULT_PORT);
		logger.l4("Using the following port for NNTP: " + port);
		
		try {
			serverSocket = new ServerSocket(Integer.valueOf(port));
			logger.l4("NNTP server started on port " + port);
			
			while (!Thread.currentThread().isInterrupted()) {
				try {
					Socket socket = serverSocket.accept();
					logger.l4("New client accepted from " + socket.getRemoteSocketAddress());
					
					// Use thread pool instead of creating new threads
					executor.submit(new NntpClient(socket));
					
				} catch (IOException e) {
					if (!Thread.currentThread().isInterrupted()) {
						logger.l2("Error accepting client connection", e);
					}
				}
			}

		} catch (IOException e) {
			Thread.currentThread().interrupt();
			logger.l1("NNTP module can't be initialised.", e);
		} finally {
			cleanup();
		}
	}
	
	private void cleanup() {
		logger.l4("Shutting down NNTP module");
		
		// Close server socket
		if (serverSocket != null && !serverSocket.isClosed()) {
			try {
				serverSocket.close();
			} catch (IOException e) {
				logger.l2("Error closing server socket", e);
			}
		}
		
		// Shutdown executor
		if (executor != null && !executor.isShutdown()) {
			executor.shutdown();
			try {
				if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
					executor.shutdownNow();
					if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
						logger.l2("Thread pool did not terminate");
					}
				}
			} catch (InterruptedException e) {
				executor.shutdownNow();
				Thread.currentThread().interrupt();
			}
		}
	}

	@Override
	public void handle(IEvent event) {

	}
}
