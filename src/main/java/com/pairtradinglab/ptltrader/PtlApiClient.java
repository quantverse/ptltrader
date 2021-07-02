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
import com.pairtradinglab.ptltrader.events.BeaconFlash;
import com.pairtradinglab.ptltrader.events.GlobalPortfolioUpdateRequest;
import com.pairtradinglab.ptltrader.events.LogEvent;
import com.pairtradinglab.ptltrader.events.PortfolioSyncOutRequest;
import com.pairtradinglab.ptltrader.events.PtlApiConnect;
import com.pairtradinglab.ptltrader.events.PtlApiProblem;
import com.pairtradinglab.ptltrader.events.StrategySyncOutRequest;
import com.pairtradinglab.ptltrader.model.*;
import com.pairtradinglab.ptltrader.trading.events.PairStateUpdated;
import com.ning.http.client.*;
import com.ning.http.client.AsyncHttpClient.BoundRequestBuilder;
import com.ning.http.client.AsyncHttpClientConfig.Builder;
import com.ning.http.client.Realm.AuthScheme;
import com.ning.http.client.providers.jdk.JDKAsyncHttpProvider;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.eventbus.*;
import com.google.common.util.concurrent.ThreadFactoryBuilder;

import java.io.IOException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;

import javax.net.ssl.SSLProtocolException;

import org.apache.log4j.Logger;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.Duration;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.picocontainer.Startable;

import net.jcip.annotations.*;

@ThreadSafe
public class PtlApiClient implements Startable {
	// dependencies to inject
	private final EventBus bus;
	private final Logger logger;
	private final Settings settings;
	private final PortfolioList portfolioList;
	private final Status status;
	private final LegHistory legHistory;
	private final TradeHistory tradeHistory;
	
	private final static String API_URL_PREFIX = "https://api.pairtradinglab.com";
	private final static String API_VERSION_HEADER = "X-PTL-Version";
	//private final static String API_URL_PREFIX2 = "https://1.2.3.4";
	
	
	private final AsyncHttpClient normalClient;
	
	private final Object stateLock = new Object();
	
	private static final ExecutorService executor = Executors.newCachedThreadPool(new ThreadFactoryBuilder().setNameFormat("async-http-%d").build());
	
	final BlockingQueue<BoundRequestBuilder> requestQueue = new LinkedBlockingQueue<>(1024);
	final Thread requestQueueWorker = new Thread(new Runnable() {
		
		@Override
		public void run() {
			while (true) {
                try {
                	BoundRequestBuilder brb = requestQueue.take();
                    logger.info("processing queued request");
                    int retrycnt=0;
                    for(;;) {
                    	Future<Response> f;
						try {
							f = brb.execute();
							Response r = f.get();
							if (r.getStatusCode()==200) {
	                    		logger.info("request processed OK");
	                    		break;
	                    	} else {
	                    		logger.warn("unable to send request, server replied with code: "+r.getStatusCode());
	                    		logger.warn(r.getResponseBody());
	                    	}
						} catch (IOException e) {
							// TODO Auto-generated catch block
							logger.warn("unable to send request, retry cnt #"+retrycnt+" msg: "+e.getMessage());
							//e.printStackTrace();
						} catch (ExecutionException e) {
							logger.warn("unable to send request, retry cnt #"+retrycnt+" msg: "+e.getMessage());
							e.printStackTrace();
						}
						Thread.sleep(10000);
						retrycnt++;
                    	
                    }
                    
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                	Thread.currentThread().interrupt();
                    //System.out.println("Interrupted via InterruptedIOException");
                    break;
                }
                
            }
			//System.out.println("Shutting down thread "+Thread.currentThread().getName());
			
		}
	}, "rq-worker");
	
	public PtlApiClient(EventBus bus, LoggerFactory loggerFactory, Settings settings,
			PortfolioList portfolioList, Status status, LegHistory legHistory, TradeHistory tradeHistory) {
		super();
		this.bus = bus;
		this.logger = loggerFactory.createLogger(this.getClass().getSimpleName());
		this.settings = settings;
		this.portfolioList = portfolioList;
		this.status = status;
		this.legHistory = legHistory;
		this.tradeHistory = tradeHistory;
		
		Builder builder = new AsyncHttpClientConfig.Builder();
		
		builder.setRequestTimeoutInMs(30000)
		    .setExecutorService(executor)
		    .build();
		
		normalClient = new AsyncHttpClient(new JDKAsyncHttpProvider(builder.build()));
		
		
	}
	
	private Realm getRealm() {
		return new Realm.RealmBuilder()
        .setPrincipal(settings.getPtlAccessKey())
        .setPassword(settings.getPtlSecretKey())
        .setUsePreemptiveAuth(true)
        .setScheme(AuthScheme.BASIC)
        .build();
	}


	
	
	public void loadPortfolios(final boolean updateOnly) {
		try {
			normalClient.prepareGet(API_URL_PREFIX + "/portfolios").addHeader(API_VERSION_HEADER, Version.getVersion()).setRealm(getRealm()).execute(new AsyncCompletionHandler<Response>(){

			    @Override
			    public Response onCompleted(Response response) throws Exception {
			        // Do something with the Response
			    	//System.out.println(response.getResponseBody()+" "+response.getStatusCode());
			        // ...
			    	if (response.getStatusCode()==401 || response.getStatusCode()==400) {
			    		bus.post(new LogEvent("authentication failure, you have been denied access to Pair Trading Lab server"));
			    		if (!updateOnly) bus.post(new PtlApiProblem("loadPortfolios", PtlApiError.ACCESS_DENIED, null, null));
			    		
			    	} else if (response.getStatusCode()==412) {
			    		// 412: precondition failed - PTL Trader version too old
			    		bus.post(new LogEvent("API error: this version of PTL Trader is not supported anymore"));
			    		if (!updateOnly) bus.post(new PtlApiProblem("loadPortfolios", PtlApiError.UNSUPPORTED_VERSION, null, null));
			    		
			    	} else if (response.getStatusCode()==200) {
			    		// success
			    		//settings.setPtlConnectEnabled(true); //@DEBUG
				    	
				    	// parse JSON
				    	ObjectMapper mapper = new ObjectMapper();
				    	try {
				    		JsonNode root = mapper.readTree(response.getResponseBody());
				    		portfolioList.updateFromJson(root);
				    		portfolioList.initialize();
				    		
				    		// send message to AmqpEngine to connect too
				    		if (!updateOnly) bus.post(new PtlApiConnect());
				    		
				    		if (updateOnly) {
				    			bus.post(new LogEvent("portfolios have been updated OK"));
				    		}
				    		
				    		// rerequest account updates if connected to IB API
							bus.post(new GlobalPortfolioUpdateRequest());
				    		
				    	} catch (JsonParseException e) {
				    		bus.post(new LogEvent("invalid server response, please try again later."));
				    		bus.post(new PtlApiProblem("loadPortfolios", PtlApiError.INVALID_RESPONSE, null, null));
				    	}
				    	
				        
			    	} else {
			    		// unknown status code, possibly failure?
			    		bus.post(new LogEvent("invalid server response, please try again later"));
			    		bus.post(new PtlApiProblem("loadPortfolios", PtlApiError.INVALID_RESPONSE, null, null));
			    		
			    	}
			    	return response;
			    	
			    }

			    @Override
			    public void onThrowable(Throwable t) {
			        // Something wrong happened.
			    	handleError(t, "loadPortfolios");
			    	
			    }
			});
		} catch (IOException e) {
			handleError(e, "loadPortfolios");
		}
		
	}
	
	
	public void loadTransactionHistories() {
		try {
			normalClient.prepareGet(API_URL_PREFIX + "/transactionhistories").addHeader(API_VERSION_HEADER, Version.getVersion()).setRealm(getRealm()).execute(new AsyncCompletionHandler<Response>(){

			    @Override
			    public Response onCompleted(Response response) throws Exception {
			        // Do something with the Response
			    	//System.out.println(response.getResponseBody()+" "+response.getStatusCode());
			        // ...
			    	if (response.getStatusCode()==401 || response.getStatusCode()==400) {
			    		logger.error("unable to load transaction histories, access denied");
			    		//bus.post(new LogEvent("Authentication failure. You have been denied access to Pair Trading Lab server."));
			    		//bus.post(new PtlApiProblem("loadPortfolios", PtlApiError.ACCESS_DENIED, null, null));
			    	} else if (response.getStatusCode()==412) {
			    		// 412: precondition failed - PTL Trader version too old
			    		bus.post(new LogEvent("unable to load transaction histories: this version of PTL Trader is not supported anymore"));
			    		
			    	} else if (response.getStatusCode()==200) {
			    		// success
			    		
				    	// parse JSON
				    	ObjectMapper mapper = new ObjectMapper();
				    	try {
				    		JsonNode root = mapper.readTree(response.getResponseBody());
				    		
				    		legHistory.addItemsFromJson(root);
				    		
				    	} catch (JsonParseException e) {
				    		//bus.post(new LogEvent("Invalid server response. Please try again later."));
				    		//bus.post(new PtlApiProblem("loadPortfolios", PtlApiError.INVALID_RESPONSE, null, null));
				    	}
				    	
				        
			    	} else {
			    		// unknown status code, possibly failure?
			    		logger.error("unable to load transaction histories, unknown status code");
			    		//bus.post(new LogEvent("Invalid server response. Please try again later."));
			    		//bus.post(new PtlApiProblem("loadPortfolios", PtlApiError.INVALID_RESPONSE, null, null));
			    		
			    	}
			    	return response;
			    	
			    }

			    @Override
			    public void onThrowable(Throwable t) {
			        // Something wrong happened.
			    	handleError(t, "loadTransactionHistories");
			    	
			    }
			});
		} catch (IOException e) {
			handleError(e, "loadTransactionHistories");
		}
		
	}
	
	
	public void loadPairTradeHistories() {
		try {
			normalClient.prepareGet(API_URL_PREFIX + "/pairtradehistories").addHeader(API_VERSION_HEADER, Version.getVersion()).setRealm(getRealm()).execute(new AsyncCompletionHandler<Response>(){

			    @Override
			    public Response onCompleted(Response response) throws Exception {
			        // Do something with the Response
			    	//System.out.println(response.getResponseBody()+" "+response.getStatusCode());
			        // ...
			    	if (response.getStatusCode()==401 || response.getStatusCode()==400) {
			    		logger.error("unable to load pair trade histories, access denied");
			    		//bus.post(new LogEvent("Authentication failure. You have been denied access to Pair Trading Lab server."));
			    		//bus.post(new PtlApiProblem("loadPortfolios", PtlApiError.ACCESS_DENIED, null, null));
			    	} else if (response.getStatusCode()==412) {
			    		// 412: precondition failed - PTL Trader version too old
			    		bus.post(new LogEvent("unable to load pair trade histories: this version of PTL Trader is not supported anymore"));
			    		
			    	} else if (response.getStatusCode()==200) {
			    		// success
			    		
				    	// parse JSON
				    	ObjectMapper mapper = new ObjectMapper();
				    	try {
				    		JsonNode root = mapper.readTree(response.getResponseBody());
				    		
				    		tradeHistory.addItemsFromJson(root);
				    		
				    	} catch (JsonParseException e) {
				    		//bus.post(new LogEvent("Invalid server response. Please try again later."));
				    		//bus.post(new PtlApiProblem("loadPortfolios", PtlApiError.INVALID_RESPONSE, null, null));
				    	}
				    	
				        
			    	} else {
			    		// unknown status code, possibly failure?
			    		logger.error("unable to load pair trade histories, unknown status code");
			    		//bus.post(new LogEvent("Invalid server response. Please try again later."));
			    		//bus.post(new PtlApiProblem("loadPortfolios", PtlApiError.INVALID_RESPONSE, null, null));
			    		
			    	}
			    	return response;
			    	
			    }

			    @Override
			    public void onThrowable(Throwable t) {
			        // Something wrong happened.
			    	handleError(t, "loadPairTradeHistories");
			    	
			    }
			});
		} catch (IOException e) {
			handleError(e, "loadPairTradeHistories");
		}
		
	}
	
	
	public void updatePortfolio(final Portfolio p) throws JsonProcessingException {
		ObjectMapper mapper = new ObjectMapper();
		
		String jsonString = mapper.writeValueAsString(p);
		//System.out.println(jsonString); //@DEBUG
		BoundRequestBuilder brb = normalClient.preparePut(API_URL_PREFIX + "/portfolios/"+p.getUid()).addHeader(API_VERSION_HEADER, Version.getVersion()).setRealm(getRealm()).setBody(jsonString).setHeader("Content-Type", "application/json");
		logger.trace("updatePortfolio: added update request to request queue");
		requestQueue.add(brb);
		
	}
	
	public void updatePairStrategyState(final PairStrategy ps) {
		ObjectMapper mapper = new ObjectMapper();
		ObjectNode node = mapper.createObjectNode();
		node.put("last_opened_equity", ps.getLastOpenEquity());
		DateTimeFormatter fmt = DateTimeFormat.forPattern("yyyy-MM-dd HH:mm:ss");
		node.put("last_opened_datetime", ps.getLastOpened().withZone(DateTimeZone.UTC).toString(fmt));
		node.set("last_model_state", mapper.valueToTree(ps.getModelState()));
		String json = node.toString();
		//System.out.println(json);
		BoundRequestBuilder brb = normalClient.preparePut(API_URL_PREFIX + "/strategies/"+ps.getUid()).setRealm(getRealm()).setBody(json).setHeader("Content-Type", "application/json");
		logger.trace("updatePairStrategyState: added update request to request queue");
		requestQueue.add(brb);
	}
	
	public void deletePairStrategy(final PairStrategy ps) {
		BoundRequestBuilder brb = normalClient.prepareDelete(API_URL_PREFIX + "/strategies/"+ps.getUid()).addHeader(API_VERSION_HEADER, Version.getVersion()).setRealm(getRealm()).setHeader("Content-Type", "application/json");
		logger.trace("deletePairStrategy: added delete request to request queue");
		requestQueue.add(brb);
	}
	
	
	private void handleError(Throwable t, String origin) {
		logger.error(t);
		PtlApiError err;
		
		if (t.getClass()==SSLProtocolException.class) {
			err = PtlApiError.SSL_NAME;
		} else {
			err = PtlApiError.UNKNOWN;
		}
		bus.post(new LogEvent("error: "+origin+": "+err.toString()));
		bus.post(new PtlApiProblem(origin, err, t.getClass().getSimpleName(), t.getMessage()));
		
	}
	
	public void bindPortfolioToAccount(final Portfolio p, final String accountCode) {
		ObjectMapper mapper = new ObjectMapper();
		ObjectNode node = mapper.createObjectNode();
		node.put("account_code", accountCode);
		String json = node.toString();
		try {
			normalClient.preparePut(API_URL_PREFIX + "/portfolios/"+p.getUid()).addHeader(API_VERSION_HEADER, Version.getVersion()).setRealm(getRealm()).setBody(json).setHeader("Content-Type", "application/json").execute(new AsyncCompletionHandler<Response>(){

			    @Override
			    public Response onCompleted(Response response) throws Exception {
			        // Do something with the Response
			    	//System.out.println(response.getResponseBody()+" "+response.getStatusCode());
			        // ...
			    	if (response.getStatusCode()==401 || response.getStatusCode()==400) {
			    		//logger.error("unable to load pair trade histories, access denied");
			    		bus.post(new LogEvent("authentication failure, you have been denied access to Pair Trading Lab server"));
			    		bus.post(new PtlApiProblem("bindPortfolioToAccount", PtlApiError.ACCESS_DENIED, null, null));
			    	} else if (response.getStatusCode()==409) {
			    		// conflict = binding rejected
			    		bus.post(new LogEvent("not allowed to bind portfolio to the account specified"));
			    		bus.post(new PtlApiProblem("bindPortfolioToAccount", PtlApiError.BIND_DENIED, null, null));
			    	} else if (response.getStatusCode()==200) {
			    		// success
			    		bus.post(new LogEvent("server side bind operation successful"));
				    	// TODO finish the bind here
			    		// re-request account updates if connected to IB API
			    		p.bind(accountCode);
						bus.post(new GlobalPortfolioUpdateRequest());
				        
			    	} else {
			    		// unknown status code, possibly failure?
			    		logger.error("unable to bind portfolio, unknown status code");
			    		bus.post(new LogEvent("invalid server response, please try again later"));
			    		bus.post(new PtlApiProblem("bindPortfolioToAccount", PtlApiError.INVALID_RESPONSE, null, null));
			    		
			    	}
			    	return response;
			    	
			    }

			    @Override
			    public void onThrowable(Throwable t) {
			        // Something wrong happened.
			    	handleError(t, "bindPortfolioToAccount");
			    	
			    }
			});
		} catch (IOException e) {
			handleError(e, "bindPortfolioToAccount");
		}
		
	}



	public static ExecutorService getExecutor() {
		return executor;
	}
	
	public void closeAll() {
		normalClient.close();
		
		
	}
	
	public void updateStrategy(final PairStrategy s) throws JsonProcessingException {
		ObjectMapper mapper = new ObjectMapper();
		
		String jsonString = mapper.writeValueAsString(s);
		//System.out.println(jsonString); //@DEBUG
		BoundRequestBuilder brb = normalClient.preparePut(API_URL_PREFIX + "/strategies/"+s.getUid()).addHeader(API_VERSION_HEADER, Version.getVersion()).setRealm(getRealm()).setBody(jsonString).setHeader("Content-Type", "application/json");
		logger.trace("updateStrategy: added update request to request queue");
		requestQueue.add(brb);
		
	}




	@Override
	public void start() {
		// TODO Auto-generated method stub
		//System.out.println("start: ptlapi");
		requestQueueWorker.start();
		bus.register(this);
	}




	@Override
	public void stop() {
		// TODO Auto-generated method stub
		//System.out.println("stop: ptlapi");
		requestQueueWorker.interrupt();
		bus.unregister(this);
	}
	
	@Subscribe
	public void onPairStateUpdated(PairStateUpdated event) {
		//@FIXME check status
		updatePairStrategyState(event.strategy);
	}
	
	@Subscribe
	public void onPortfolioSyncOutRequest(PortfolioSyncOutRequest event) {
		try {
			updatePortfolio(event.p);
		} catch (JsonProcessingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	@Subscribe
	public void onStrategySyncOutRequest(StrategySyncOutRequest event) {
		try {
			updateStrategy(event.s);
		} catch (JsonProcessingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

}
