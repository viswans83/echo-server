package com.sankar.echo;

import java.io.IOException;
import java.net.BindException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.apache.commons.cli.BasicParser;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.OptionBuilder;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

public class EchoServer {
	
	private static int BUFFER_SIZE = 256;
	
	private int port;
	
	private Selector selector;
	
	private Map<SelectionKey,ByteBuffer> buffmap = new HashMap<>();
	
	private boolean shutdown;
	
	private Thread serverThread;
	
	public EchoServer(int port) {
		this.port = port;
	}
	
	public void start() throws IOException {
		serverThread = Thread.currentThread();
		
		SocketAddress addr = new InetSocketAddress("localhost",port);
		ServerSocketChannel sc = ServerSocketChannel.open();
		
		sc.configureBlocking(false);
		sc.bind(addr);
		
		selector = Selector.open();
		sc.register(selector, SelectionKey.OP_ACCEPT);
		
		while(!shutdown) {
			int n = selector.select();
			if(n > 0) {
				Iterator<SelectionKey> selectedSet = selector.selectedKeys().iterator();
				while(selectedSet.hasNext()) {
					SelectionKey key = selectedSet.next();
					
					selectedSet.remove();
					if(key.isAcceptable())
						accept(key);
					else if (key.isReadable())
						read(key);
					else if (key.isWritable())
						write(key);
				}
			}
		}
		
		for(SelectionKey key : buffmap.keySet())
			key.channel().close();
		
		sc.close();
		selector.close();
	}
	
	public void shutDown() {
		this.shutdown = true;
		
		try {
			selector.wakeup();
			serverThread.join();
		} catch (InterruptedException e) {
			// Ignore - cannot be interrupted
		}
	}
	
	private void accept(SelectionKey key) throws IOException {
		ServerSocketChannel ssc = (ServerSocketChannel)key.channel();
		
		SocketChannel sc = ssc.accept();
		System.out.println("Connected - " + sc.getRemoteAddress());
		
		sc.write(ByteBuffer.wrap("Java NIO Echo Server\n".getBytes()));
		
		sc.configureBlocking(false);
		sc.register(selector, SelectionKey.OP_READ);
		
		buffmap.put(sc.keyFor(selector), ByteBuffer.allocate(BUFFER_SIZE));
	}
	
	private void read(SelectionKey key) throws IOException {
		SocketChannel sc = (SocketChannel)key.channel();
		ByteBuffer buff = buffmap.get(key);
		
		int nread = sc.read(buff);
		
		if(nread == -1) {
			System.out.println("Disconnected - " + sc.getRemoteAddress());
			
			buffmap.remove(key);
			sc.close();
		} else {
			buff.flip();
			key.interestOps(SelectionKey.OP_WRITE);
		}
	}
	
	private void write(SelectionKey key) throws IOException {
		SocketChannel sc = (SocketChannel)key.channel();
		ByteBuffer buff = buffmap.get(key);
		
		sc.write(buff);
		
		if(!buff.hasRemaining()) {
			buff.clear();
			key.interestOps(SelectionKey.OP_READ);
		}
	}
	
	@SuppressWarnings("static-access")
	public static void main(String[] args) {
		Options opts = new Options();
		
		Option port = OptionBuilder.withDescription("specify custom port").withArgName("port").hasArg().create("port");
		opts.addOption(port);
		
		CommandLineParser parser = new BasicParser();
		
		try {
			CommandLine cmd = parser.parse( opts, args);
			if(cmd.getArgs().length > 0) throw new IllegalArgumentException();
			
			int serverPort = cmd.hasOption("port") ? Integer.valueOf(cmd.getOptionValue("port")) : 3000;
			
			final EchoServer server = new EchoServer(serverPort);
			
			Runtime.getRuntime().addShutdownHook(new Thread() {
				@Override
				public void run() {
					server.shutDown();
					System.out.println("Server stopped");
				}
			});
			
			System.out.println("Starting NIO Echo Server on port " + serverPort);
			
			server.start();
		} catch(ParseException | IllegalArgumentException ex) {
			HelpFormatter formatter = new HelpFormatter();
			formatter.printHelp("echoserver", opts);
		} catch(BindException ex) {
			System.out.println("Could not bind to port");
		}catch (IOException e) {
			System.out.println("An error occured: " + e.getMessage());
		}
	}

}
