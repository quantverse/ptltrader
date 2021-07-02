/**
 * 	This file is part of PTL Trader.
 *
 * 	Copyright © 2011-2021 Quantverse OÜ. All Rights Reserved.
 *
 *  PTL Trader is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  PTL Trader is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with PTL Trader. If not, see <https://www.gnu.org/licenses/>.
 */
package com.pairtradinglab.ptltrader;

import java.io.IOException;
import java.util.concurrent.TimeoutException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.Map;

import org.apache.log4j.Logger;
import org.joda.time.DateTime;
import org.joda.time.Duration;
import org.picocontainer.Startable;

import com.google.common.collect.ImmutableMap;
import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import com.pairtradinglab.ptltrader.events.AmqpConnect;
import com.pairtradinglab.ptltrader.events.AmqpProblem;
import com.pairtradinglab.ptltrader.events.PtlApiConnect;
import com.pairtradinglab.ptltrader.events.SerializedEvent;
import com.pairtradinglab.ptltrader.model.Settings;
import com.pairtradinglab.ptltrader.model.Status;
import com.rabbitmq.client.*;

import java.util.concurrent.LinkedTransferQueue;

public class AmqpEngine implements Startable {
	private static final String AMQP_HOST = "amqp.pairtradinglab.com";
	
	static final Map<String, Object> exchangeParams = ImmutableMap.of(
	       
	);
	
	private final Settings settings;
	private final Status status;
	private final EventBus bus;
	private final Logger logger;
	
	Connection conn=null;
	Channel channel=null;
	Channel inputChannel=null;
	private boolean authOk=false;
	private DateTime lastConnectAttempt;
	
	private final LinkedTransferQueue<AmqpControlMessage> messages = new LinkedTransferQueue<AmqpControlMessage>();
	private final Thread cthread = new Thread(new Runnable() {
		
		@Override
		public void run() {
			while (true) {
                try {
                	AmqpControlMessage message = messages.take();
                    handleMessage(message);
                    
                    //Thread.sleep(1000);
                } catch (InterruptedException e) {
                	Thread.currentThread().interrupt();
                    //System.out.println("Interrupted via InterruptedIOException");
                    break;
                }
                
            }
			//System.out.println("Shutting down thread "+Thread.currentThread().getName()); //@DEBUG
			
		}
	}, "amqp");

	public AmqpEngine(Settings settings, Status status, EventBus bus, LoggerFactory loggerFactory) {
		super();
		this.settings = settings;
		this.status = status;
		this.bus = bus;
		this.logger = loggerFactory.createLogger(this.getClass().getSimpleName());
		
	}

	@Override
	public void start() {
		logger.debug("starting AMQP engine");
		bus.register(this);
		cthread.start();
		
	}

	@Override
	public void stop() {
		logger.debug("stopping AMQP engine");
		bus.unregister(this);
		messages.put(new AmqpControlMessage(AmqpControlMessage.TYPE_SHUTDOWN, null, DateTime.now()));
		
	}
	
	/**
	 * Confined to amqp thread
	 */
	private void disconnect() {
		try {
			if (channel!=null && channel.isOpen()) channel.close();
			if (inputChannel!=null && inputChannel.isOpen()) inputChannel.close();
			if (conn!=null && conn.isOpen()) conn.close();
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	/**
	 * Confined to amqp thread
	 */
	private void shutdown() {
		logger.debug("shutting down AMQP engine connections and threads");
		disconnect();
		Thread.currentThread().interrupt();
	}
	
	
	/**
	 * Confined to amqp thread
	 * @param message
	 */
	private void handleMessage(final AmqpControlMessage message) {
		switch(message.type) {
			case AmqpControlMessage.TYPE_CONNECT:
				connect();
				break;
			case AmqpControlMessage.TYPE_SHUTDOWN:
				shutdown();
				break;
			case AmqpControlMessage.TYPE_EVENT:
				handleEvent(message);
				break;
		}
		
	}
	
	/**
	 * Confined to amqp thread
	 * @throws IOException
	 */
	private void createChannel() throws IOException {
		// check connection
		if (conn==null || !conn.isOpen()) return; // do nothing
		// if we have channel already, close it first
		if (channel!=null) {
			try {
				channel.close();
			} catch (Exception e) {
			}
		}
		// create a new cannel
		logger.debug("creating new amqp channel");
		channel = conn.createChannel();
		logger.trace("channel created, isOpen="+channel.isOpen());
		channel.addShutdownListener(new ShutdownListener() {
			
			@Override
			public void shutdownCompleted(ShutdownSignalException se) {
				logger.warn("channel shutdown detected "+se.getReason());
				
				
			}
		});
		
		//!channel.exchangeDeclare("ptl.clients", "direct", false, true, false, exchangeParams);
		
		
	}
	
	/**
	 * Confined to amqp thread
	 * @throws IOException
	 */
	private void createInputChannel() throws IOException {
		// check connection
		if (conn==null || !conn.isOpen()) return; // do nothing
		// if we have channel already, close it first
		if (inputChannel!=null) {
			try {
				inputChannel.close();
			} catch (Exception e) {
			}
		}
		// create a new cannel
		logger.debug("creating new amqp input channel");
		inputChannel = conn.createChannel();
		logger.trace("input channel created, isOpen="+inputChannel.isOpen());
		inputChannel.addShutdownListener(new ShutdownListener() {
			
			@Override
			public void shutdownCompleted(ShutdownSignalException se) {
				logger.warn("input channel shutdown detected "+se.getReason());
				
				
			}
		});
		
		String inputQueueName = inputChannel.queueDeclare().getQueue();
		logger.trace("input queue name: "+inputQueueName);
		inputChannel.queueBind(inputQueueName, "ptl."+settings.getPtlAccessKey(), "");
		
		boolean autoAck = false;
		inputChannel.basicConsume(inputQueueName, autoAck, "myConsumerTag",
		     new DefaultConsumer(inputChannel) {
		         @Override
		         public void handleDelivery(String consumerTag,
		                                    Envelope envelope,
		                                    AMQP.BasicProperties properties,
		                                    byte[] body)
		             throws IOException
		         {
		             String routingKey = envelope.getRoutingKey();
		             String contentType = properties.getContentType();
		             long deliveryTag = envelope.getDeliveryTag();
		             // (process the message components here ...)
		             logger.info("got message: "+routingKey);
		             inputChannel.basicAck(deliveryTag, false);
		         }
		     });
		
		
	}
	
	/**
	 * Confined to amqp thread
	 */
	private void connect() {
		lastConnectAttempt = DateTime.now();
		status.setPtlConnected(false);
		settings.setPtlConnectEnabled(false);
		
		if (conn!=null) {
			if (conn.isOpen())
				try {
					conn.close();
				} catch (Exception e) {
					// ignore
				}
		}
		
		
		ConnectionFactory factory = new ConnectionFactory();
		factory.setHost(AMQP_HOST);
		factory.setUsername(settings.getPtlAccessKey());
		factory.setPassword(settings.getPtlSecretKey());
		factory.setVirtualHost("/");
		
		try {
			factory.useSslProtocol();
		} catch (KeyManagementException | NoSuchAlgorithmException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
			return;
		}
		
		
		
		try {
			logger.debug("connecting to amqp");
			conn = factory.newConnection();
			conn.addShutdownListener(new ShutdownListener() {
				
				@Override
				public void shutdownCompleted(ShutdownSignalException se) {
					logger.warn("amqp connection shutdown detected: "+se.getReason());
					status.setPtlConnected(false);
					settings.setPtlConnectEnabled(true);
					
				}
			});
			createChannel();
			createInputChannel();
			logger.debug("AMQP connected OK open: "+conn.isOpen()+" channel: "+channel.isOpen());
			status.setPtlConnected(true);
			settings.setPtlConnectEnabled(false);
			authOk=true;
			bus.post(new AmqpConnect()); // to handle autostart chain in Application
		} catch (PossibleAuthenticationFailureException e2) {
			logger.error("amqp auth: "+e2.getMessage());
			conn = null;
			bus.post(new AmqpProblem(AmqpError.AUTH_FAIL, e2.getClass().getSimpleName(), e2.getMessage()));
			authOk=false;
			settings.setPtlConnectEnabled(true);
		} catch (ShutdownSignalException e3) {
			logger.error("amqp shutdown: "+e3.getMessage());
		} catch (IOException e1) {
			// TODO Auto-generated catch block
			logger.error("amqp io: "+e1.getMessage());
			bus.post(new AmqpProblem(AmqpError.CONNECT_FAIL, e1.getClass().getSimpleName(), e1.getMessage()));
			conn = null;
        } catch (TimeoutException e4) {
            // TODO Auto-generated catch block
            logger.error("amqp timeout: "+e4.getMessage());
            bus.post(new AmqpProblem(AmqpError.CONNECT_FAIL, e4.getClass().getSimpleName(), e4.getMessage()));
            conn = null;
		}
	}
	
	@Subscribe
	// called from master event bus only
	public void onPtlApiConnect(PtlApiConnect event) {
		messages.put(new AmqpControlMessage(AmqpControlMessage.TYPE_CONNECT, null, DateTime.now()));
		
	}
	
	
	/**
	 * Confined to amqp thread
	 * @param event
	 */
	private void handleEvent(AmqpControlMessage message) {
		SerializedEvent sev = (SerializedEvent) message.data;
		//logger.debug("engine got serialized event"); //@DEBUG
		if (!authOk) return; // ignore
		
		for(;;) {
			// retry loop
			byte[] messageBodyBytes = sev.event.getBytes();
			try {
				logger.trace("publishing serialized event, routing key="+sev.className); //@DEBUG
				// check if still have connection
				if (conn==null || !conn.isOpen()) {
					if (sev.important) {
						connect();
					} else {
						if (!requestReconnect()) return;
					}
					
				}
				// check if still have channel
				if (channel==null || !channel.isOpen()) {
					if (sev.important) {
						createChannel();
					} else return;
				}
				
				if (sev.important) {
					channel.basicPublish("ptl.clients", sev.className,
					        new AMQP.BasicProperties.Builder()
					          .contentType("application/json").userId(settings.getPtlAccessKey())
					          .deliveryMode(2)
					          .build(),
					        messageBodyBytes);
				} else {
					channel.basicPublish("ptl.clients", sev.className,
					        new AMQP.BasicProperties.Builder()
					          .contentType("application/json").userId(settings.getPtlAccessKey())
					          .build(),
					        messageBodyBytes);
				}
				
				
				logger.trace("published"); //@DEBUG
				break;
			} catch (AlreadyClosedException e2) {
				logger.error(e2.getMessage()+": "+e2.getReason());
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			if (!sev.important) break; // break here to avoid retrying posting unimportant event
			logger.debug("this event is important, retrying amqp publish in 20 secs");
			try {
				Thread.sleep(20000);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				logger.debug("thread has been interrupted");
				break;
			}
		}
		
	}
	
	@Subscribe
	public void onSerializedEvent(SerializedEvent event) {
		messages.put(new AmqpControlMessage(AmqpControlMessage.TYPE_EVENT, event, DateTime.now()));

	}
	
	/**
	 * Confined to amqp thread
	 * @param event
	 */
	private boolean requestReconnect() {
		if (conn!=null && conn.isOpen()) return true; // nothing needed to do
		Duration d = new Duration(lastConnectAttempt, DateTime.now());
		if (d.getStandardSeconds()<30) return false; // prevent reconnecting too often
		connect();
		return true;
	}

}
