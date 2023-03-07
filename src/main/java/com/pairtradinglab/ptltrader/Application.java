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

import com.pairtradinglab.ptltrader.trading.events.*;
import org.eclipse.core.databinding.beans.typed.BeanProperties;
import org.eclipse.jface.databinding.swt.typed.WidgetProperties;
import org.eclipse.jface.databinding.viewers.typed.ViewerProperties;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.SashForm;
import org.eclipse.swt.widgets.CoolBar;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.TabFolder;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.CoolItem;
import org.eclipse.swt.widgets.TabItem;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.MenuItem;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.Text;
import org.eclipse.swt.widgets.Group;
import org.eclipse.wb.swt.SWTResourceManager;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.layout.RowLayout;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.core.databinding.DataBindingContext;
import org.eclipse.core.databinding.observable.value.IObservableValue;
import org.eclipse.jface.databinding.fieldassist.ControlDecorationSupport;
import org.eclipse.core.databinding.observable.Realm;
import org.eclipse.jface.databinding.swt.SWTObservables;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.databinding.viewers.ObservableListContentProvider;
import org.eclipse.core.databinding.observable.map.IObservableMap;
import org.eclipse.core.databinding.beans.BeansObservables;
import org.eclipse.jface.databinding.viewers.ObservableMapLabelProvider;
import org.eclipse.core.databinding.observable.list.IObservableList;
import org.eclipse.jface.dialogs.MessageDialog;

import it.sauronsoftware.junique.AlreadyLockedException;
import it.sauronsoftware.junique.JUnique;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.eclipse.core.databinding.UpdateValueStrategy;
import org.apache.log4j.Logger;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.common.eventbus.AsyncEventBus;
import com.google.common.eventbus.EventBus;

import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.RowData;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.jface.viewers.ComboViewer;
import org.joda.time.DateTime;
import org.picocontainer.Characteristics;
import org.picocontainer.DefaultPicoContainer;
import org.picocontainer.MutablePicoContainer;
import org.picocontainer.behaviors.Caching;

import com.tictactec.ta.lib.MAType;

import org.eclipse.swt.widgets.Spinner;

import com.pairtradinglab.ptltrader.events.AmqpConnect;
import com.pairtradinglab.ptltrader.events.AmqpProblem;
import com.pairtradinglab.ptltrader.events.IbConnectionFailed;
import com.pairtradinglab.ptltrader.events.LogEvent;
import com.pairtradinglab.ptltrader.events.PtlApiProblem;
import com.pairtradinglab.ptltrader.ib.SimpleWrapper;
import com.pairtradinglab.ptltrader.model.*;
import com.pairtradinglab.ptltrader.model.converter.Boolean2Led;
import com.pairtradinglab.ptltrader.model.converter.IbConnected2String;
import com.pairtradinglab.ptltrader.model.converter.Inverter;
import com.pairtradinglab.ptltrader.model.converter.SimpleInt2String;
import com.pairtradinglab.ptltrader.trading.ActivityDetector;
import com.pairtradinglab.ptltrader.trading.HistoricalDataProviderFactoryImpl;
import com.pairtradinglab.ptltrader.trading.MarketDataProvider;
import com.pairtradinglab.ptltrader.trading.PairDataProviderFactoryImpl;
import com.pairtradinglab.ptltrader.trading.PairTradingCoreFactoryImpl;
import com.google.common.eventbus.*;
import com.google.common.util.concurrent.ThreadFactoryBuilder;

import com.pairtradinglab.ptltrader.model.validator.StringNotEmpty;

import org.eclipse.core.databinding.Binding;

import com.pairtradinglab.ptltrader.model.validator.MarginPercents;
import com.pairtradinglab.ptltrader.model.validator.SlotOccupation;

import org.eclipse.swt.events.ShellAdapter;
import org.eclipse.swt.events.ShellEvent;
import org.eclipse.swt.widgets.ProgressBar;
import org.eclipse.swt.events.DisposeListener;
import org.eclipse.swt.events.DisposeEvent;

public class Application {
	private Binding textPairXtraMinExpectBinding;
	private Binding textPairXtraMinPriceBinding;
	private Binding textPairXtraMinPlBinding;
	private Binding textPairBasicSlotOccupationBinding;
	private Binding textPairMargin2Binding;
	private Binding textPairMargin1Binding;
	private Binding textPortfNameBinding;
	
	
	// dependencies to inject
	private final PortfolioList mPortfolioList;
	private final PtlApiClient apiClient;
	private final Status mStatus;
	private final LogEntryList mLogEntryList;
	private final TradeHistory mTradeHistory;
	private final Settings mSettings;
	private final AccountList mAccountList;
	private final Beacon beacon;
	private final Logger logger;
	private final EventBus bus;
	private final List<SimpleWrapper> ibWrappers;
	private final AmqpEngine amqpEngine;
	private final AmqpProxy amqpProxy;
	private final LegHistory mLegHistory;
	private final RuntimeParams runtimeParams;
	private final SystemMonitor systemMonitor;
	private final Set<String> connectedAccounts;
	
	
	public Application(PortfolioList mPortfolioList, PtlApiClient apiClient,
			Status mStatus, LogEntryList mLogEntryList,
			TradeHistory mTradeHistory, Settings mSettings,
			AccountList mAccountList, Beacon beacon, Logger logger,
			EventBus bus, List<SimpleWrapper> ibWrappers, AmqpEngine amqpEngine, AmqpProxy amqpProxy, LegHistory mLegHistory,
			RuntimeParams runtimeParams, SystemMonitor systemMonitor, Set<String> connectedAccounts) {
		super();
		this.mPortfolioList = mPortfolioList;
		this.apiClient = apiClient;
		this.mStatus = mStatus;
		this.mLogEntryList = mLogEntryList;
		this.mTradeHistory = mTradeHistory;
		this.mSettings = mSettings;
		this.mAccountList = mAccountList;
		this.beacon = beacon;
		this.logger = logger;
		this.bus = bus;
		this.ibWrappers = ibWrappers;
		this.amqpEngine = amqpEngine;
		this.amqpProxy = amqpProxy;
		this.mLegHistory = mLegHistory;
		this.runtimeParams = runtimeParams;
		this.systemMonitor = systemMonitor;
		this.connectedAccounts = connectedAccounts;
		
	}
	
	private SimpleWrapper getWrapper() {
		return ibWrappers.get(0);
	
	}
	
	private AboutDialog aboutDialog;
	
	private DataBindingContext m_bindingContext;

	protected Shell shlPtlTrader;
	private Text textPTLAccessKey;
	private Text textPTLAccessToken;
	private Text textIbClientId;
	private Text textIbHost;
	private Text textIbPort;
	private Text textIbFaAccount;
	private Text textPtlStatus;
	
	
	
	
	private Table tablePortfolios;
	private TableViewer tableViewerPortfolios;
	private Table tablePortfPairs;
	private TableViewer tableViewerPortfPairs;
	private Table tablePairPositionList;
	private Table tableLog;
	private Table tableHistory;
	private TableViewer tableViewerPairPositionList;
	private TableViewer tableViewerLog;
	private TableViewer tableViewerHistory;
	private Text textIbStatus;
	private Button btnIbConnect;
	private Label lblIbStatusLed;
	private Label lblPTLStatusLed;
	private Text textPairBasicStock1;
	private Text textPairBasicStock2;

	private Text textPairBasicModel;
	private Text textPairBasicSlotOccupation;
	private Text textPairModelEntryThreshold;
	private Text textPairModelExitThreshold;
    private Text textPairModelDowntickThreshold;
	private Text textPairModelMaxEntryScore;
	private Text textPairModelKalmanAutoVe;
	private Text textPairModelKalmanAutoUsageTarget;
	private Text textPairModelRatioMaType;
	private Text textPairModelRatioMaPeriod;
	private Text textPairModelRatioRsiPeriod;
	private Text textPairModelRatioRsiThreshold;
	private Text textPairModelRatioStddevPeriod;
	private Text textPairModelResidualLinRegPeriod;
	private Combo comboPairModelRatioEntryMode;
	private Combo comboPairModelNeutrality;
	private Spinner spinPairXtraMaxDays;
	private Text textPairXtraMinPl;
	private Text textPairXtraMinPrice;
	private Text textPairXtraMinExpect;
	private Button btnCheckPairXtraMaxDays;
	private Button btnCheckPairXtraMinPl;
	private Button btnCheckPairXtraMinPrice;
	private Button btnCheckPairAllowReversals;
	private Label lblPairXtraMinExpect;
	private Button btnCheckPairXtraMinExpect;
	private Text textPairTimezone;
	private Text textPairMargin1;
	private Text textPairMargin2;
	private Spinner spinPairEntryTimeStartHour;
	private Spinner spinPairEntryTimeStartMinute;
	private Spinner spinPairEntryTimeEndHour;
	private Spinner spinPairEntryTimeEndMinute;
	private Spinner spinPairExitTimeStartHour;
	private Spinner spinExitTimeStartMinute;
	private Spinner spinPairExitTimeEndHour;
	private Spinner spinPairExitTimeEndMinute;
	private Combo comboPairRestriction;
	private Combo comboPairStatus;
	private Text textPortfName;
	private Text textPortfPairs;
	private Text textPortfBoundTo;
	private Spinner spinnerPortfMaxPairsOpen;
	private Spinner spinnerPortfAllocPerc;
	private Button btnPortfBind;
	private Combo comboPortfMasterStatus;
	private Combo comboPortfDayTrading;

	private ComboViewer comboViewerPortfAccounts;

	private Combo comboPortfAccounts;
	private Button btnPortfUnbind;
	private Button btnPairClose;
	private Button btnPairDelete;
	private Button btnPairResume;
	private Button btnPairResetLastOpened;
	private Button btnPairOpenLong;
	private Button btnPairOpenShort;
	private Button btnPTLConnect;
	
	
	private static final ExecutorService busExecutor = Executors.newFixedThreadPool(5, new ThreadFactoryBuilder().setNameFormat("bus-master-%d").build());
	private static final ExecutorService amqpBusExecutor = Executors.newSingleThreadExecutor(new ThreadFactoryBuilder().setNameFormat("amqp-bus").build());
	private Table tableLegHistory;
	private TableViewer tableViewerLegHistory;
	MenuItem mntmReloadPortfolios;
	private Table tableAccounts;
	private TableViewer tableViewerAccounts;
	private Button btnPTLSaveToken;
	private Button btnConfidentialMode;
	
	private boolean firstTimeActivated=true;
	private boolean autoStarted=false;
	private ProgressBar progressBarPairSlotUsage;
	
	private Image images[] = {null, null, null};
	

	/**
	 * Launch the application.
	 * @param args
	 */
	public static void main(String[] args) {
		
		Display display = Display.getDefault();
		final RuntimeParams runtimeParams = new RuntimeParams(args);
		
		// check if instance is already running
		final String iid = Application.class.getName()+"."+runtimeParams.getProfile();
		try {
			JUnique.acquireLock(iid);
			
		} catch (AlreadyLockedException e) {
			System.out.println("Error: PTL Trader is already running for profile: "+runtimeParams.getProfile());
			return;
		}
		
		Realm.runWithDefault(SWTObservables.getRealm(display), new Runnable() {
			public void run() {
				try {
					// setup object graph and create application instance
					MutablePicoContainer pico = new DefaultPicoContainer(new Caching());
					
					pico.as(Characteristics.USE_NAMES).addComponent(Application.class);
					pico.addComponent(runtimeParams);
					pico.addComponent("bus", new AsyncEventBus("bus_master", busExecutor));
					//pico.addComponent("amqpBus", new AsyncEventBus("amqp_bus", amqpBusExecutor));
					pico.addComponent(LoggerFactoryImpl.class);
					
					LoggerFactory lf = (LoggerFactory) pico.getComponent(LoggerFactory.class);
					Logger l = lf.createLogger("PTLTrader");
					pico.addComponent(l);
					pico.as(Characteristics.USE_NAMES).addComponent(SystemMonitor.class);
					pico.as(Characteristics.USE_NAMES).addComponent(Beacon.class);
					pico.addComponent(AccountList.class);
					pico.addComponent(Settings.class);
					pico.addComponent(TradeHistory.class);
					pico.addComponent(LogEntryList.class);
					pico.addComponent(LegHistory.class);
					pico.addComponent(Status.class);
					pico.addComponent(StringXorProcessor.class);
					pico.addComponent(ActiveCores.class);
					pico.addComponent(ActivityDetector.class);
					pico.as(Characteristics.USE_NAMES).addComponent(PtlApiClient.class);
					pico.addComponent(PortfolioList.class);
					pico.as(Characteristics.USE_NAMES).addComponent(PortfolioFactoryImpl.class);
					pico.as(Characteristics.USE_NAMES).addComponent(PairStrategyFactoryImpl.class);
					pico.as(Characteristics.USE_NAMES).addComponent(PairTradingCoreFactoryImpl.class);
					pico.as(Characteristics.USE_NAMES).addComponent(MarketDataProvider.class);
					pico.as(Characteristics.USE_NAMES).addComponent(PairDataProviderFactoryImpl.class);
					
					pico.as(Characteristics.USE_NAMES).addComponent(AmqpEngine.class);
					pico.as(Characteristics.USE_NAMES).addComponent(AmqpProxy.class);
					
					Set<String> connectedAccounts = Collections.synchronizedSet(new HashSet<String>());
					pico.addComponent(connectedAccounts);
					
					Map<String,SimpleWrapper> wrapperMap = Collections.synchronizedMap(new HashMap<String, SimpleWrapper>());
					pico.addComponent(wrapperMap);
					ArrayList<SimpleWrapper> wrapperList = new ArrayList<SimpleWrapper>();
					pico.addComponent(wrapperList);
					pico.addComponent(HistoricalDataProviderFactoryImpl.class);
					pico.as(Characteristics.USE_NAMES).addComponent(SimpleWrapper.class);
					
					SimpleWrapper sw = (SimpleWrapper) pico.getComponent(SimpleWrapper.class);
					wrapperList.add(0, sw);
					
					Application window = (Application) pico.getComponent(Application.class);
					pico.start();
					
					window.open();
					l.info("shutting down");
					
					// we need to stop pair trading cores (all threads) here
					PortfolioList pl = (PortfolioList) pico.getComponent(PortfolioList.class);
					ActiveCores cores = (ActiveCores) pico.getComponent(ActiveCores.class);
					pl.stopAllCores();
					int remaining;
					while ((remaining=cores.getActiveCores())>0) {
						l.debug(String.format("waiting for %d core threads to finish", remaining));
						Thread.sleep(1000);
					}
					
					l.debug("shutting down executors");
					pico.stop();
					busExecutor.shutdownNow();
					amqpBusExecutor.shutdownNow();
					JUnique.releaseLock(iid);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		});
		
		
	}

	/**
	 * Open the window.
	 */
	public void open() {
		logger.info("application started: "+runtimeParams);
    	logger.trace("trace check");
		Display display = Display.getDefault();
    		    
		setDefaultValues();
		createContents();
		aboutDialog = new AboutDialog(shlPtlTrader, SWT.PRIMARY_MODAL);
		shlPtlTrader.open();
		shlPtlTrader.layout();
		while (!shlPtlTrader.isDisposed()) {
			if (!display.readAndDispatch()) {
				display.sleep();
			}
		}
		apiClient.closeAll();
		PtlApiClient.getExecutor().shutdownNow();
		if (getWrapper().getIbSocket().isConnected()) {
			logger.debug("disconnecting IB");
			getWrapper().getIbSocket().eDisconnect();
		}
		logger.info("Application terminated");
	}
	
	
	private void setDefaultValues() {
		//mStatus.setPtlStatus("huhu!");
		bus.register(this);
		bus.register(mLogEntryList);
		bus.register(mTradeHistory);
		bus.register(mLegHistory);
		
		//mPortfolioList.initialize(ibWrapper.getIbSocket(), mSettings.getIbClientId());
		
		bus.post(new LogEvent("application started"));
		
		
	}
	
	@Subscribe
	public void onAmqpConnect(AmqpConnect event) {
		if (autoStarted || !runtimeParams.isAutoStart()) return;
		autoStarted=true;
		connectToIb();
	}

	

	/**
	 * Create contents of the window.
	 */
	protected void createContents() {
		shlPtlTrader = new Shell();
		shlPtlTrader.addDisposeListener(new DisposeListener() {
			public void widgetDisposed(DisposeEvent arg0) {
				SWTResourceManager.dispose();
			}
		});
		shlPtlTrader.addShellListener(new ShellAdapter() {
			@Override
			public void shellActivated(ShellEvent e) {
				if (firstTimeActivated) {
					firstTimeActivated=false;
					if (runtimeParams.isAutoStart()) {
						connectToPtl();
					}
					
				}
			}
		});
		
		images[0]=SWTResourceManager.getImage(Application.class, "/com/pairtradinglab/ptltrader/favicon2_16.png");
		images[1]=SWTResourceManager.getImage(Application.class, "/com/pairtradinglab/ptltrader/favicon2_32.png");
		images[2]=SWTResourceManager.getImage(Application.class, "/com/pairtradinglab/ptltrader/favicon2_48.png");
		
		shlPtlTrader.setImages(images);
		shlPtlTrader.setMinimumSize(new Point(800, 480));
		shlPtlTrader.setSize(450, 300);
		shlPtlTrader.setText("PTL Trader");
		shlPtlTrader.setLayout(new GridLayout(1, false));
		
		SashForm sashForm1 = new SashForm(shlPtlTrader, SWT.BORDER | SWT.VERTICAL);
		sashForm1.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 1, 1));
		
		SashForm sashForm2 = new SashForm(sashForm1, SWT.NONE);
		
		tableViewerPortfolios = new TableViewer(sashForm2, SWT.BORDER | SWT.FULL_SELECTION);
		tableViewerPortfolios.setColumnProperties(new String[] {});
		tablePortfolios = tableViewerPortfolios.getTable();
		tablePortfolios.setLinesVisible(true);
		tablePortfolios.setHeaderVisible(true);
		
		TableColumn tblclmnPortfsName = new TableColumn(tablePortfolios, SWT.NONE);
		tblclmnPortfsName.setWidth(180);
		tblclmnPortfsName.setText("Portfolio");
		
		TableColumn tblclmnPortfsPairs = new TableColumn(tablePortfolios, SWT.RIGHT);
		tblclmnPortfsPairs.setWidth(60);
		tblclmnPortfsPairs.setText("Pairs");
		
		TableColumn tblclmnPortfsPl = new TableColumn(tablePortfolios, SWT.RIGHT);
		tblclmnPortfsPl.setWidth(80);
		tblclmnPortfsPl.setText("P/L");
		
		TableColumn tblclmnPortfsAccount = new TableColumn(tablePortfolios, SWT.NONE);
		tblclmnPortfsAccount.setWidth(100);
		tblclmnPortfsAccount.setText("Account");
		
		TabFolder tabFolderPortfolio = new TabFolder(sashForm2, SWT.NONE);
		
		TabItem tbtmPortfPairs = new TabItem(tabFolderPortfolio, SWT.NONE);
		tbtmPortfPairs.setText("Pairs");
		
		Composite compositePairList = new Composite(tabFolderPortfolio, SWT.NONE);
		tbtmPortfPairs.setControl(compositePairList);
		GridLayout gl_compositePairList = new GridLayout(2, false);
		gl_compositePairList.verticalSpacing = 0;
		gl_compositePairList.marginWidth = 0;
		gl_compositePairList.marginHeight = 0;
		gl_compositePairList.horizontalSpacing = 0;
		compositePairList.setLayout(gl_compositePairList);
		
		
		tableViewerPortfPairs = new TableViewer(compositePairList, SWT.BORDER | SWT.FULL_SELECTION);
		tablePortfPairs = tableViewerPortfPairs.getTable();
		tablePortfPairs.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 1, 1));
		tablePortfPairs.setLinesVisible(true);
		tablePortfPairs.setHeaderVisible(true);
		
		TableColumn tblclmnPairStock1 = new TableColumn(tablePortfPairs, SWT.NONE);
		tblclmnPairStock1.setWidth(100);
		tblclmnPairStock1.setText("Stock 1");
		
		TableColumn tblclmnPairStock2 = new TableColumn(tablePortfPairs, SWT.NONE);
		tblclmnPairStock2.setWidth(100);
		tblclmnPairStock2.setText("Stock 2");
		
		TableColumn tblclmnPairModel = new TableColumn(tablePortfPairs, SWT.NONE);
		tblclmnPairModel.setWidth(60);
		tblclmnPairModel.setText("Model");
		
		TableColumn tblclmnPairStatus = new TableColumn(tablePortfPairs, SWT.NONE);
		tblclmnPairStatus.setWidth(100);
		tblclmnPairStatus.setText("Status");
		
		TableColumn tblclmnCoreStatus = new TableColumn(tablePortfPairs, SWT.NONE);
		tblclmnCoreStatus.setWidth(170);
		tblclmnCoreStatus.setText("Engine Status");
		
		TableColumn tblclmnPairPL = new TableColumn(tablePortfPairs, SWT.RIGHT);
		tblclmnPairPL.setWidth(80);
		tblclmnPairPL.setText("P/L");
		
		TableColumn tblclmnPairZScoreBid = new TableColumn(tablePortfPairs, SWT.RIGHT);
		tblclmnPairZScoreBid.setWidth(90);
		tblclmnPairZScoreBid.setText("Z-Score Bid");

		TableColumn tblclmnPairZScoreAsk = new TableColumn(tablePortfPairs, SWT.RIGHT);
		tblclmnPairZScoreAsk.setWidth(90);
		tblclmnPairZScoreAsk.setText("Z-Score Ask");
		
		TableColumn tblclmnPairRsi = new TableColumn(tablePortfPairs, SWT.RIGHT);
		tblclmnPairRsi.setWidth(60);
		tblclmnPairRsi.setText("RSI");
		
		TableColumn tblclmnPairProfitPotential = new TableColumn(tablePortfPairs, SWT.RIGHT);
		tblclmnPairProfitPotential.setWidth(80);
		tblclmnPairProfitPotential.setText("ProfitP");
		
		TableColumn tblclmnPairLastOpened = new TableColumn(tablePortfPairs, SWT.NONE);
		tblclmnPairLastOpened.setWidth(140);
		tblclmnPairLastOpened.setText("Last Opened");
		
		TableColumn tblclmnPairDaysRemaining = new TableColumn(tablePortfPairs, SWT.RIGHT);
		tblclmnPairDaysRemaining.setWidth(70);
		tblclmnPairDaysRemaining.setText("Days 2 Go");
		
		progressBarPairSlotUsage = new ProgressBar(compositePairList, SWT.VERTICAL);
		progressBarPairSlotUsage.setToolTipText("Slot Usage");
		progressBarPairSlotUsage.setLayoutData(new GridData(SWT.RIGHT, SWT.FILL, false, true, 1, 1));
		
		
		TabItem tbtmPortfSettings = new TabItem(tabFolderPortfolio, SWT.NONE);
		tbtmPortfSettings.setToolTipText("Portfolio Settings");
		tbtmPortfSettings.setText("Settings");
		
		Composite compositePortfSettings = new Composite(tabFolderPortfolio, SWT.NONE);
		compositePortfSettings.setBackground(SWTResourceManager.getColor(SWT.COLOR_WIDGET_BACKGROUND));
		tbtmPortfSettings.setControl(compositePortfSettings);
		compositePortfSettings.setLayout(new GridLayout(2, false));
		
		Group groupPortfGeneral = new Group(compositePortfSettings, SWT.NONE);
		groupPortfGeneral.setLayout(new GridLayout(2, false));
		groupPortfGeneral.setLayoutData(new GridData(SWT.LEFT, SWT.TOP, false, false, 1, 1));
		groupPortfGeneral.setText("General");
		groupPortfGeneral.setBounds(0, 0, 70, 82);
		
		Label lblPortfName = new Label(groupPortfGeneral, SWT.NONE);
		lblPortfName.setBounds(0, 0, 55, 15);
		lblPortfName.setText("Name:");
		
		textPortfName = new Text(groupPortfGeneral, SWT.BORDER);
		GridData gd_textPortfName = new GridData(SWT.LEFT, SWT.CENTER, false, false, 1, 1);
		gd_textPortfName.widthHint = 150;
		textPortfName.setLayoutData(gd_textPortfName);
		textPortfName.setBounds(0, 0, 76, 21);
		
		Label lblPortfPairs = new Label(groupPortfGeneral, SWT.NONE);
		lblPortfPairs.setBounds(0, 0, 55, 15);
		lblPortfPairs.setText("Pairs:");
		
		textPortfPairs = new Text(groupPortfGeneral, SWT.BORDER | SWT.RIGHT);
		textPortfPairs.setEditable(false);
		textPortfPairs.setBounds(0, 0, 76, 21);
		
		Label lblPortfMaxPairsOpen = new Label(groupPortfGeneral, SWT.NONE);
		lblPortfMaxPairsOpen.setBounds(0, 0, 55, 15);
		lblPortfMaxPairsOpen.setText("Slots:");
		
		spinnerPortfMaxPairsOpen = new Spinner(groupPortfGeneral, SWT.BORDER);
		spinnerPortfMaxPairsOpen.setEnabled(false);
		spinnerPortfMaxPairsOpen.setMinimum(1);
		spinnerPortfMaxPairsOpen.setBounds(0, 0, 47, 22);
		
		Label lblPortfAllocPerc = new Label(groupPortfGeneral, SWT.NONE);
		lblPortfAllocPerc.setToolTipText("Use this setting to allocate percentage of account for this portfolio (100 = use whole account margin)");
		lblPortfAllocPerc.setBounds(0, 0, 55, 15);
		lblPortfAllocPerc.setText("Allocate Account %:");
		
		spinnerPortfAllocPerc = new Spinner(groupPortfGeneral, SWT.BORDER);
		spinnerPortfAllocPerc.setMinimum(10);
		spinnerPortfAllocPerc.setBounds(0, 0, 47, 22);
		
		Group groupPortfTrading = new Group(compositePortfSettings, SWT.NONE);
		groupPortfTrading.setText("Trading");
		groupPortfTrading.setLayout(new GridLayout(3, false));
		groupPortfTrading.setLayoutData(new GridData(SWT.LEFT, SWT.TOP, false, false, 1, 1));
		groupPortfTrading.setBounds(0, 0, 70, 82);
		
		Label lblPortfBoundTo = new Label(groupPortfTrading, SWT.NONE);
		lblPortfBoundTo.setBounds(0, 0, 55, 15);
		lblPortfBoundTo.setText("Bound to account:");
		
		textPortfBoundTo = new Text(groupPortfTrading, SWT.BORDER);
		textPortfBoundTo.setEditable(false);
		textPortfBoundTo.setBounds(0, 0, 76, 21);
		
		btnPortfUnbind = new Button(groupPortfTrading, SWT.NONE);
		btnPortfUnbind.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				ISelection selp = tableViewerPortfolios.getSelection();
				if (selp.isEmpty() || !(selp instanceof StructuredSelection)) return;
				Object portfolio=((StructuredSelection) selp).getFirstElement();
				
				if (portfolio instanceof Portfolio) {
					if (!((Portfolio) portfolio).getAccountCode().isEmpty()) {
						boolean confirmed = MessageDialog.openConfirm(
								shlPtlTrader, 
								"Portfolio Binding Confirmation", 
								"Are you sure to unbind this portfolio from IB account? Trading will stop immediately, positions will remain open."
						);
						if (confirmed) {
							String logentry = String.format("unbinding portfolio [%s]", ((Portfolio) portfolio).getName());
							logger.info(logentry);
							bus.post(new LogEvent(logentry));
							((Portfolio) portfolio).unbind();
							try {
								apiClient.updatePortfolio((Portfolio) portfolio);
							} catch (JsonProcessingException e1) {
								logger.error("unable to sync change with PTL site, JSON processing error");
								
							}
							
							
						}
					} else {
						MessageDialog.openError(shlPtlTrader, "Error", "This portfolio is not bound to any account.");
						return;
					}
					
				}
			}
			
		});
		btnPortfUnbind.setBounds(0, 0, 75, 25);
		btnPortfUnbind.setText("Unbind");
		
		Label lblPortfBindTo = new Label(groupPortfTrading, SWT.NONE);
		lblPortfBindTo.setBounds(0, 0, 55, 15);
		lblPortfBindTo.setText("Bind to account:");
		
		comboViewerPortfAccounts = new ComboViewer(groupPortfTrading, SWT.NONE);
		comboPortfAccounts = comboViewerPortfAccounts.getCombo();
		GridData gd_comboPortfAccounts = new GridData(SWT.LEFT, SWT.CENTER, false, false, 1, 1);
		gd_comboPortfAccounts.widthHint = 70;
		comboPortfAccounts.setLayoutData(gd_comboPortfAccounts);
		//comboPortfAccounts.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false, 2, 1));
		
		
		btnPortfBind = new Button(groupPortfTrading, SWT.NONE);
		btnPortfBind.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				
				ISelection selp = tableViewerPortfolios.getSelection();
				if (selp.isEmpty() || !(selp instanceof StructuredSelection)) return;
				ISelection sela = comboViewerPortfAccounts.getSelection();
				if (sela.isEmpty() || !(sela instanceof StructuredSelection)) return;
				
				if (!getWrapper().getIbSocket().isConnected()) {
					MessageDialog.openError(shlPtlTrader, "Error", "Please connect to IB API first.");
					return;
				}
				
				Object portfolio=((StructuredSelection) selp).getFirstElement();
				Object account=((StructuredSelection) sela).getFirstElement();
				if (portfolio instanceof Portfolio && account instanceof Account) {
					if (((Portfolio) portfolio).getAccountCode().isEmpty()) {
						// check if there is another portfolio bound to this account already
						for (Portfolio p:(List<Portfolio>) mPortfolioList.getPortfolios()) {
							if (p.getAccountCode().equals(((Account) account).getCode())) {
								MessageDialog.openError(shlPtlTrader, "Bind Error", "There is another portfolio already bound to this account.");
								return;
							}
						}
						
						if (!((Portfolio) portfolio).checkFeatures()) {
							MessageDialog.openError(shlPtlTrader, "Error", "This portfolio uses features not supported in this PTL Trader version.");
							return;
						}
						
						String confirmMsg;
						if (((Portfolio) portfolio).getMasterStatus()==Portfolio.MASTER_STATUS_ACTIVE) {
							confirmMsg=String.format("Are you sure to bind this portfolio to IB account #%s? Trading will start immediately.\n\nBy clicking \"OK\" you are agreeing to the following conditions: Trade placement and execution may be delayed or fail due to market volatility and volume, quote delays, incorrect historical data, system and software errors, Internet traffic, outages and other factors. You accept that solely you are taking those risks, and that Quantverse OÜ cannot be held responsible by you if they occur, regardless of the reason.", ((Account) account).getCode());
						} else {
							confirmMsg=String.format("Are you sure to bind this portfolio to IB account #%s?", ((Account) account).getCode());
							
						}
						boolean confirmed = MessageDialog.openConfirm(
								shlPtlTrader, 
								"Portfolio Binding Confirmation", 
								confirmMsg
						);
						if (confirmed) {
							String logentry = String.format("binding portfolio [%s] to account %s", ((Portfolio) portfolio).getName(), ((Account) account).getCode());
							logger.info(logentry);
							bus.post(new LogEvent(logentry));
							apiClient.bindPortfolioToAccount((Portfolio) portfolio, ((Account) account).getCode());
							
						}
					} else {
						MessageDialog.openError(shlPtlTrader, "Error", "This portfolio is already bound to another account.");
						return;
					}
					
				}
			}
		});
		btnPortfBind.setBounds(0, 0, 75, 25);
		btnPortfBind.setText("Bind");
		
		Label lblPortfMasterStatus = new Label(groupPortfTrading, SWT.NONE);
		lblPortfMasterStatus.setBounds(0, 0, 55, 15);
		lblPortfMasterStatus.setText("Master Status:");
		
		comboPortfMasterStatus = new Combo(groupPortfTrading, SWT.NONE);
		comboPortfMasterStatus.setItems(new String[] {"Fully Inactive", "No New Positions", "Fully Active"});
		comboPortfMasterStatus.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false, 2, 1));
		comboPortfMasterStatus.setBounds(0, 0, 91, 23);
		
		Label lblPortfDayTrading = new Label(groupPortfTrading, SWT.NONE);
		lblPortfDayTrading.setToolTipText("Pattern DayTrader rules protection settings");
		lblPortfDayTrading.setBounds(0, 0, 55, 15);
		lblPortfDayTrading.setText("PDT Rules Protection:");
		
		comboPortfDayTrading = new Combo(groupPortfTrading, SWT.NONE);
		comboPortfDayTrading.setItems(new String[] {"No Daytrading", "Allow Daytrading If Equity > 25k"});
		comboPortfDayTrading.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false, 2, 1));
		comboPortfDayTrading.setBounds(0, 0, 91, 23);
		sashForm2.setWeights(new int[] {1, 2});
		
		TabFolder tabFolderPairDetail = new TabFolder(sashForm1, SWT.NONE);
		
		TabItem tbtmPairPositions = new TabItem(tabFolderPairDetail, SWT.NONE);
		tbtmPairPositions.setText("Pair Legs");
		
		Composite compositePositionList = new Composite(tabFolderPairDetail, SWT.NONE);
		tbtmPairPositions.setControl(compositePositionList);
		compositePositionList.setLayout(new FillLayout(SWT.HORIZONTAL));
		
		tableViewerPairPositionList = new TableViewer(compositePositionList, SWT.BORDER | SWT.FULL_SELECTION);
		tablePairPositionList = tableViewerPairPositionList.getTable();
		tablePairPositionList.setLinesVisible(true);
		tablePairPositionList.setHeaderVisible(true);
		
		TableColumn tblclmnPosSymbol = new TableColumn(tablePairPositionList, SWT.NONE);
		tblclmnPosSymbol.setWidth(100);
		tblclmnPosSymbol.setText("Symbol");

		TableColumn tblclmnPosSecType = new TableColumn(tablePairPositionList, SWT.NONE);
		tblclmnPosSecType.setWidth(70);
		tblclmnPosSecType.setText("SecType");
		
		TableColumn tblclmnPosLast = new TableColumn(tablePairPositionList, SWT.RIGHT);
		tblclmnPosLast.setWidth(80);
		tblclmnPosLast.setText("Last");
		
		TableColumn tblclmnPosBid = new TableColumn(tablePairPositionList, SWT.RIGHT);
		tblclmnPosBid.setWidth(80);
		tblclmnPosBid.setText("Bid");
		
		TableColumn tblclmnPosAsk = new TableColumn(tablePairPositionList, SWT.RIGHT);
		tblclmnPosAsk.setWidth(80);
		tblclmnPosAsk.setText("Ask");
		
		TableColumn tblclmnPosBidSize = new TableColumn(tablePairPositionList, SWT.RIGHT);
		tblclmnPosBidSize.setWidth(80);
		tblclmnPosBidSize.setText("Bid Size");
		
		TableColumn tblclmnPosAskSize = new TableColumn(tablePairPositionList, SWT.RIGHT);
		tblclmnPosAskSize.setWidth(80);
		tblclmnPosAskSize.setText("Ask Size");
		
		TableColumn tblclmnPosStatus = new TableColumn(tablePairPositionList, SWT.NONE);
		tblclmnPosStatus.setWidth(100);
		tblclmnPosStatus.setText("Position");
		
		TableColumn tblclmnPosQty = new TableColumn(tablePairPositionList, SWT.RIGHT);
		tblclmnPosQty.setWidth(100);
		tblclmnPosQty.setText("Qty");
		
		TableColumn tblclmnPosPL = new TableColumn(tablePairPositionList, SWT.RIGHT);
		tblclmnPosPL.setWidth(100);
		tblclmnPosPL.setText("P/L");
		
		TableColumn tblclmnPosAvgOpenPrice = new TableColumn(tablePairPositionList, SWT.RIGHT);
		tblclmnPosAvgOpenPrice.setWidth(100);
		tblclmnPosAvgOpenPrice.setText("Avg Open Price");
		
		TableColumn tblclmnPosValue = new TableColumn(tablePairPositionList, SWT.RIGHT);
		tblclmnPosValue.setWidth(100);
		tblclmnPosValue.setText("Value");
		
		TableColumn tblclmnPosShortable = new TableColumn(tablePairPositionList, SWT.CENTER);
		tblclmnPosShortable.setWidth(100);
		tblclmnPosShortable.setText("Shortable");
		
		TabItem tbtmPairSettings = new TabItem(tabFolderPairDetail, SWT.NONE);
		tbtmPairSettings.setText("Settings: Model && Rules");
		
		Composite compositePairSettings = new Composite(tabFolderPairDetail, SWT.NONE);
		compositePairSettings.setBackground(SWTResourceManager.getColor(SWT.COLOR_WIDGET_BACKGROUND));
		tbtmPairSettings.setControl(compositePairSettings);
		compositePairSettings.setLayout(new GridLayout(3, false));
		
		Group groupPairBasicInfo = new Group(compositePairSettings, SWT.NONE);
		groupPairBasicInfo.setForeground(SWTResourceManager.getColor(0, 0, 0));
		groupPairBasicInfo.setLayoutData(new GridData(SWT.LEFT, SWT.TOP, false, false, 1, 1));
		groupPairBasicInfo.setText("Basic Info");
		groupPairBasicInfo.setLayout(new GridLayout(2, false));
		
		Label lblPairBasicStock1 = new Label(groupPairBasicInfo, SWT.NONE);
		lblPairBasicStock1.setText("Stock 1:");
		
		textPairBasicStock1 = new Text(groupPairBasicInfo, SWT.BORDER);
		GridData gd_textPairBasicStock1 = new GridData(SWT.LEFT, SWT.CENTER, false, false, 1, 1);
		gd_textPairBasicStock1.widthHint = 110;
		textPairBasicStock1.setLayoutData(gd_textPairBasicStock1);
		textPairBasicStock1.setToolTipText("");
		textPairBasicStock1.setEditable(false);
		textPairBasicStock1.setBounds(0, 0, 76, 21);
		
		Label lblPairBasicStock2 = new Label(groupPairBasicInfo, SWT.NONE);
		lblPairBasicStock2.setBounds(0, 0, 55, 15);
		lblPairBasicStock2.setText("Stock 2:");
		
		textPairBasicStock2 = new Text(groupPairBasicInfo, SWT.BORDER);
		GridData gd_textPairBasicStock2 = new GridData(SWT.LEFT, SWT.CENTER, false, false, 1, 1);
		gd_textPairBasicStock2.widthHint = 110;
		textPairBasicStock2.setLayoutData(gd_textPairBasicStock2);
		textPairBasicStock2.setEditable(false);
		textPairBasicStock2.setBounds(0, 0, 76, 21);
		
		
		Label lblPairBasicModel = new Label(groupPairBasicInfo, SWT.NONE);
		lblPairBasicModel.setBounds(0, 0, 55, 15);
		lblPairBasicModel.setText("Model:");
		
		textPairBasicModel = new Text(groupPairBasicInfo, SWT.BORDER);
		gd_textPairBasicStock2.widthHint = 110;
		textPairBasicModel.setEditable(false);
		textPairBasicModel.setBounds(0, 0, 76, 21);
		
		
		Label lblPairBasicSlotOccupation = new Label(groupPairBasicInfo, SWT.NONE);
		lblPairBasicSlotOccupation.setBounds(0, 0, 55, 15);
		lblPairBasicSlotOccupation.setText("Slot Occupation:");
		
		textPairBasicSlotOccupation = new Text(groupPairBasicInfo, SWT.BORDER | SWT.RIGHT);
		GridData gd_textPairBasicSlotOccupation = new GridData(SWT.LEFT, SWT.CENTER, false, false, 1, 1);
		gd_textPairBasicSlotOccupation.widthHint = 60;
		textPairBasicSlotOccupation.setLayoutData(gd_textPairBasicSlotOccupation);
		gd_textPairBasicStock2.widthHint = 110;
		textPairBasicSlotOccupation.setBounds(0, 0, 76, 21);
		
		Group groupPairModelSettings = new Group(compositePairSettings, SWT.NONE);
		groupPairModelSettings.setLayout(new GridLayout(6, false));
		groupPairModelSettings.setLayoutData(new GridData(SWT.LEFT, SWT.TOP, false, false, 1, 1));
		groupPairModelSettings.setText("Model Settings");
		
		Label lblPairModelEntryThreshold = new Label(groupPairModelSettings, SWT.NONE);
		lblPairModelEntryThreshold.setText("Entry Threshold:");
		
		textPairModelEntryThreshold = new Text(groupPairModelSettings, SWT.BORDER | SWT.RIGHT);
		textPairModelEntryThreshold.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, true, false, 1, 1));
		textPairModelEntryThreshold.setEditable(false);
		
		Label lblPairModelExitThreshold = new Label(groupPairModelSettings, SWT.NONE);
		lblPairModelExitThreshold.setText("Exit Threshold:");
		
		textPairModelExitThreshold = new Text(groupPairModelSettings, SWT.BORDER | SWT.RIGHT);
		textPairModelExitThreshold.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, true, false, 1, 1));
		textPairModelExitThreshold.setEditable(false);

        Label lblPairModelDowntickThreshold = new Label(groupPairModelSettings, SWT.NONE);
        lblPairModelDowntickThreshold.setText("DT Threshold:");

        textPairModelDowntickThreshold = new Text(groupPairModelSettings, SWT.BORDER | SWT.RIGHT);
        textPairModelDowntickThreshold.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, true, false, 1, 1));
        textPairModelDowntickThreshold.setEditable(false);

		Label lblPairModelRatioMaType = new Label(groupPairModelSettings, SWT.NONE);
		lblPairModelRatioMaType.setToolTipText("Ratio model moving average type");
		lblPairModelRatioMaType.setText("MA Type:");
		
		textPairModelRatioMaType = new Text(groupPairModelSettings, SWT.BORDER);
		textPairModelRatioMaType.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, true, false, 1, 1));
		textPairModelRatioMaType.setEditable(false);
		
		Label lblPairModelRatioMaPeriod = new Label(groupPairModelSettings, SWT.NONE);
		lblPairModelRatioMaPeriod.setToolTipText("Ratio model moving average period in days");
		lblPairModelRatioMaPeriod.setText("MA Period:");
		
		textPairModelRatioMaPeriod = new Text(groupPairModelSettings, SWT.BORDER | SWT.RIGHT);
		textPairModelRatioMaPeriod.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, true, false, 1, 1));
		textPairModelRatioMaPeriod.setEditable(false);
		
		Label lblPairModelRatioStddevPeriod = new Label(groupPairModelSettings, SWT.NONE);
		lblPairModelRatioStddevPeriod.setToolTipText("Ratio model standard deviation period in days");
		lblPairModelRatioStddevPeriod.setText("Stddev Period:");
		
		textPairModelRatioStddevPeriod = new Text(groupPairModelSettings, SWT.BORDER | SWT.RIGHT);
		textPairModelRatioStddevPeriod.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, true, false, 1, 1));
		textPairModelRatioStddevPeriod.setEditable(false);

        Label lblPairModelRatioEntryMode = new Label(groupPairModelSettings, SWT.NONE);
        lblPairModelRatioEntryMode.setToolTipText("Strategy entry mode");
        lblPairModelRatioEntryMode.setText("Entry Mode:");
        comboPairModelRatioEntryMode = new Combo(groupPairModelSettings, SWT.NONE);
        comboPairModelRatioEntryMode.setEnabled(false);
        comboPairModelRatioEntryMode.setItems(new String[] {"Simple", "Uptick", "Downtick"});

		Label lblPairModelRatioRsiPeriod = new Label(groupPairModelSettings, SWT.NONE);
		lblPairModelRatioRsiPeriod.setToolTipText("Ratio model RSI period in days");
		lblPairModelRatioRsiPeriod.setText("RSI Period:");
		textPairModelRatioRsiPeriod = new Text(groupPairModelSettings, SWT.BORDER | SWT.RIGHT);
		textPairModelRatioRsiPeriod.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, true, false, 1, 1));
		textPairModelRatioRsiPeriod.setEditable(false);
		
		Label lblPairModelRatioRsiThreshold = new Label(groupPairModelSettings, SWT.NONE);
		lblPairModelRatioRsiThreshold.setToolTipText("Ratio model RSI threshold, 0 = RSI disabled");
		lblPairModelRatioRsiThreshold.setText("RSI Threshold:");
		textPairModelRatioRsiThreshold = new Text(groupPairModelSettings, SWT.BORDER | SWT.RIGHT);
		textPairModelRatioRsiThreshold.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, true, false, 1, 1));
		textPairModelRatioRsiThreshold.setEditable(false);

        Label lblPairModelNeutrality = new Label(groupPairModelSettings, SWT.NONE);
        lblPairModelNeutrality.setToolTipText("Equity allocation algorithm (model neutrality, for Kalman-grid and Kalman-auto only)");
        lblPairModelNeutrality.setText("Neutrality:");
        comboPairModelNeutrality = new Combo(groupPairModelSettings, SWT.NONE);
        comboPairModelNeutrality.setEnabled(false);
        comboPairModelNeutrality.setItems(new String[] {"Dollar", "Beta"});

        Label lblPairModelResidualLinRegPeriod = new Label(groupPairModelSettings, SWT.NONE);
        lblPairModelResidualLinRegPeriod.setToolTipText("Residual model linear regression period in days");
        lblPairModelResidualLinRegPeriod.setText("LinReg Period:");

        textPairModelResidualLinRegPeriod = new Text(groupPairModelSettings, SWT.BORDER | SWT.RIGHT);
        textPairModelResidualLinRegPeriod.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, true, false, 1, 1));
        textPairModelResidualLinRegPeriod.setEditable(false);


        Label lblPairModelMaxEntryScore = new Label(groupPairModelSettings, SWT.NONE);
        lblPairModelMaxEntryScore.setText("Max Score:");
        textPairModelMaxEntryScore = new Text(groupPairModelSettings, SWT.BORDER | SWT.RIGHT);
        textPairModelMaxEntryScore.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, true, false, 1, 1));
        textPairModelMaxEntryScore.setEditable(false);

		Label lblPairModelKalmanAutoVe = new Label(groupPairModelSettings, SWT.NONE);
		lblPairModelKalmanAutoVe.setText("KA Ve:");
		lblPairModelKalmanAutoVe.setToolTipText("Kalman Auto model observation covariance Ve");
		textPairModelKalmanAutoVe = new Text(groupPairModelSettings, SWT.BORDER | SWT.RIGHT);
		textPairModelKalmanAutoVe.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, true, false, 1, 1));
		textPairModelKalmanAutoVe.setEditable(false);

		Label lblPairModelKalmanUsageTarget = new Label(groupPairModelSettings, SWT.NONE);
		lblPairModelKalmanUsageTarget.setText("KA Usage Target:");
		lblPairModelKalmanUsageTarget.setToolTipText("Kalman Auto model slot time usage target (%)");
		textPairModelKalmanAutoUsageTarget = new Text(groupPairModelSettings, SWT.BORDER | SWT.RIGHT);
		textPairModelKalmanAutoUsageTarget.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, true, false, 1, 1));
		textPairModelKalmanAutoUsageTarget.setEditable(false);
		
		Group groupPairXtraRules = new Group(compositePairSettings, SWT.NONE);
		groupPairXtraRules.setLayout(new GridLayout(3, false));
		groupPairXtraRules.setLayoutData(new GridData(SWT.LEFT, SWT.TOP, false, false, 1, 1));
		groupPairXtraRules.setText("Extra Rules");
		
		Label lblPairXtraMaxDays = new Label(groupPairXtraRules, SWT.NONE);
		lblPairXtraMaxDays.setToolTipText("Max days in position, after expired, position is automatically closed");
		lblPairXtraMaxDays.setText("Max Days:");
		
		spinPairXtraMaxDays = new Spinner(groupPairXtraRules, SWT.BORDER);
		spinPairXtraMaxDays.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false, 1, 1));
		
		btnCheckPairXtraMaxDays = new Button(groupPairXtraRules, SWT.CHECK);
		
		Label lblPairXtraMinPl = new Label(groupPairXtraRules, SWT.NONE);
		lblPairXtraMinPl.setToolTipText("Min Profit/Loss for position to allow closing by exit signal. Does not apply to closing by timeout.");
		lblPairXtraMinPl.setText("Min P/L to close:");
		
		textPairXtraMinPl = new Text(groupPairXtraRules, SWT.BORDER | SWT.RIGHT);
		GridData gd_textPairXtraMinPl = new GridData(SWT.LEFT, SWT.CENTER, false, false, 1, 1);
		gd_textPairXtraMinPl.widthHint = 60;
		textPairXtraMinPl.setLayoutData(gd_textPairXtraMinPl);
		
		btnCheckPairXtraMinPl = new Button(groupPairXtraRules, SWT.CHECK);
		
		Label lblPairXtraMinPrice = new Label(groupPairXtraRules, SWT.NONE);
		lblPairXtraMinPrice.setToolTipText("Minimum instrument price to allow entry");
		lblPairXtraMinPrice.setText("Min Price:");
		
		textPairXtraMinPrice = new Text(groupPairXtraRules, SWT.BORDER | SWT.RIGHT);
		GridData gd_textPairXtraMinPrice = new GridData(SWT.LEFT, SWT.CENTER, false, false, 1, 1);
		gd_textPairXtraMinPrice.widthHint = 60;
		textPairXtraMinPrice.setLayoutData(gd_textPairXtraMinPrice);
		
		btnCheckPairXtraMinPrice = new Button(groupPairXtraRules, SWT.CHECK);
		
		lblPairXtraMinExpect = new Label(groupPairXtraRules, SWT.NONE);
		lblPairXtraMinExpect.setToolTipText("Minimum trade expectation in USD.");
		lblPairXtraMinExpect.setText("Min Profit Potential:");
		
		textPairXtraMinExpect = new Text(groupPairXtraRules, SWT.BORDER | SWT.RIGHT);
		GridData gd_textPairXtraMinExpect = new GridData(SWT.LEFT, SWT.CENTER, false, false, 1, 1);
		gd_textPairXtraMinExpect.widthHint = 60;
		textPairXtraMinExpect.setLayoutData(gd_textPairXtraMinExpect);
		
		btnCheckPairXtraMinExpect = new Button(groupPairXtraRules, SWT.CHECK);
		
		TabItem tbtmPairSettings2 = new TabItem(tabFolderPairDetail, SWT.NONE);
		tbtmPairSettings2.setText("Settings: Trading Hours && Maintenance");
		
		Composite compositePairSettings2 = new Composite(tabFolderPairDetail, SWT.NONE);
		compositePairSettings2.setBackground(SWTResourceManager.getColor(SWT.COLOR_WIDGET_BACKGROUND));
		tbtmPairSettings2.setControl(compositePairSettings2);
		compositePairSettings2.setLayout(new GridLayout(3, false));
		
		Group groupPairTimeSettings = new Group(compositePairSettings2, SWT.NONE);
		groupPairTimeSettings.setText("Trading Hours");
		groupPairTimeSettings.setLayoutData(new GridData(SWT.LEFT, SWT.TOP, false, false, 1, 1));
		groupPairTimeSettings.setBounds(0, 0, 70, 82);
		groupPairTimeSettings.setLayout(new GridLayout(6, false));
		
		Label lblPairEntryTimeStart = new Label(groupPairTimeSettings, SWT.NONE);
		lblPairEntryTimeStart.setBounds(0, 0, 55, 15);
		lblPairEntryTimeStart.setText("Entry Start Time:");
		
		spinPairEntryTimeStartHour = new Spinner(groupPairTimeSettings, SWT.BORDER);
		spinPairEntryTimeStartHour.setMaximum(23);
		spinPairEntryTimeStartHour.setBounds(0, 0, 47, 22);
		
		spinPairEntryTimeStartMinute = new Spinner(groupPairTimeSettings, SWT.BORDER);
		spinPairEntryTimeStartMinute.setMaximum(59);
		spinPairEntryTimeStartMinute.setBounds(0, 0, 47, 22);
		
		Label lblPairEntryTimeEnd = new Label(groupPairTimeSettings, SWT.NONE);
		lblPairEntryTimeEnd.setBounds(0, 0, 55, 15);
		lblPairEntryTimeEnd.setText("End Time:");
		
		spinPairEntryTimeEndHour = new Spinner(groupPairTimeSettings, SWT.BORDER);
		spinPairEntryTimeEndHour.setMaximum(23);
		spinPairEntryTimeEndHour.setBounds(0, 0, 47, 22);
		
		spinPairEntryTimeEndMinute = new Spinner(groupPairTimeSettings, SWT.BORDER);
		spinPairEntryTimeEndMinute.setMaximum(59);
		spinPairEntryTimeEndMinute.setBounds(0, 0, 47, 22);
		
		Label lblPairExitTimeStart = new Label(groupPairTimeSettings, SWT.NONE);
		lblPairExitTimeStart.setBounds(0, 0, 55, 15);
		lblPairExitTimeStart.setText("Exit Start Time:");
		
		spinPairExitTimeStartHour = new Spinner(groupPairTimeSettings, SWT.BORDER);
		spinPairExitTimeStartHour.setMaximum(23);
		spinPairExitTimeStartHour.setBounds(0, 0, 47, 22);
		
		spinExitTimeStartMinute = new Spinner(groupPairTimeSettings, SWT.BORDER);
		spinExitTimeStartMinute.setMaximum(59);
		spinExitTimeStartMinute.setBounds(0, 0, 47, 22);
		
		Label lblPairExitTimeEnd = new Label(groupPairTimeSettings, SWT.NONE);
		lblPairExitTimeEnd.setBounds(0, 0, 55, 15);
		lblPairExitTimeEnd.setText("End Time:");
		
		spinPairExitTimeEndHour = new Spinner(groupPairTimeSettings, SWT.BORDER);
		spinPairExitTimeEndHour.setMaximum(23);
		spinPairExitTimeEndHour.setBounds(0, 0, 47, 22);
		
		spinPairExitTimeEndMinute = new Spinner(groupPairTimeSettings, SWT.BORDER);
		spinPairExitTimeEndMinute.setMaximum(59);
		spinPairExitTimeEndMinute.setBounds(0, 0, 47, 22);
		
		Label lblNewLabel_4 = new Label(groupPairTimeSettings, SWT.NONE);
		lblNewLabel_4.setToolTipText("Timezone used for evaluating start/end times. Should be equal to timezone local to the exchange where you trade this pair.");
		lblNewLabel_4.setBounds(0, 0, 55, 15);
		lblNewLabel_4.setText("Timezone");
		
		textPairTimezone = new Text(groupPairTimeSettings, SWT.BORDER);
		textPairTimezone.setEditable(false);
		GridData gd_textPairTimezone = new GridData(SWT.LEFT, SWT.CENTER, true, false, 5, 1);
		gd_textPairTimezone.widthHint = 200;
		textPairTimezone.setLayoutData(gd_textPairTimezone);
		textPairTimezone.setBounds(0, 0, 76, 21);
		new Label(groupPairTimeSettings, SWT.NONE);
		new Label(groupPairTimeSettings, SWT.NONE);
		new Label(groupPairTimeSettings, SWT.NONE);
		new Label(groupPairTimeSettings, SWT.NONE);
		new Label(groupPairTimeSettings, SWT.NONE);
		new Label(groupPairTimeSettings, SWT.NONE);
		
		Group groupPairTradingSettings = new Group(compositePairSettings2, SWT.NONE);
		groupPairTradingSettings.setLayout(new GridLayout(2, false));
		groupPairTradingSettings.setLayoutData(new GridData(SWT.LEFT, SWT.TOP, false, false, 1, 1));
		groupPairTradingSettings.setText("Margin && Positions");
		groupPairTradingSettings.setBounds(0, 0, 70, 82);
		
		Label lblPairRestriction = new Label(groupPairTradingSettings, SWT.NONE);
		lblPairRestriction.setBounds(0, 0, 55, 15);
		lblPairRestriction.setText("Allow Positions:");
		
		comboPairRestriction = new Combo(groupPairTradingSettings, SWT.NONE);
		comboPairRestriction.setItems(new String[] {"Long & Short", "Long Only", "Short Only"});
		comboPairRestriction.setBounds(0, 0, 91, 23);

		Label lblAllowReversals = new Label(groupPairTradingSettings, SWT.NONE);
		lblAllowReversals.setBounds(0, 0, 55, 15);
		lblAllowReversals.setText("Allow Reversals:");

		btnCheckPairAllowReversals = new Button(groupPairTradingSettings, SWT.CHECK);
		
		Label lblPairMargin1 = new Label(groupPairTradingSettings, SWT.NONE);
		lblPairMargin1.setBounds(0, 0, 55, 15);
		lblPairMargin1.setText("Stock 1 Margin %:");
		
		textPairMargin1 = new Text(groupPairTradingSettings, SWT.BORDER | SWT.RIGHT);
		GridData gd_textPairMargin1 = new GridData(SWT.LEFT, SWT.CENTER, false, false, 1, 1);
		gd_textPairMargin1.widthHint = 60;
		textPairMargin1.setLayoutData(gd_textPairMargin1);
		textPairMargin1.setBounds(0, 0, 76, 21);
		
		Label lblPairMargin2 = new Label(groupPairTradingSettings, SWT.NONE);
		lblPairMargin2.setBounds(0, 0, 55, 15);
		lblPairMargin2.setText("Stock 2 Margin %:");
		
		textPairMargin2 = new Text(groupPairTradingSettings, SWT.BORDER | SWT.RIGHT);
		GridData gd_textPairMargin2 = new GridData(SWT.LEFT, SWT.CENTER, false, false, 1, 1);
		gd_textPairMargin2.widthHint = 60;
		textPairMargin2.setLayoutData(gd_textPairMargin2);
		textPairMargin2.setBounds(0, 0, 76, 21);
		
		Group groupPairManagement = new Group(compositePairSettings2, SWT.NONE);
		groupPairManagement.setText("Maintenance");
		groupPairManagement.setLayout(new GridLayout(3, false));
		groupPairManagement.setLayoutData(new GridData(SWT.LEFT, SWT.TOP, false, false, 1, 1));
		groupPairManagement.setBounds(0, 0, 70, 82);
		
		Label lblPairStatus = new Label(groupPairManagement, SWT.NONE);
		lblPairStatus.setBounds(0, 0, 55, 15);
		lblPairStatus.setText("Status:");
		
		comboPairStatus = new Combo(groupPairManagement, SWT.NONE);
		comboPairStatus.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false, 2, 1));
		comboPairStatus.setItems(new String[] {"Fully Inactive", "No New Positions", "Fully Active"});
		comboPairStatus.setBounds(0, 0, 91, 23);
		
		btnPairOpenLong = new Button(groupPairManagement, SWT.NONE);
		btnPairOpenLong.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				ISelection sel = tableViewerPortfPairs.getSelection();
				if (!sel.isEmpty() && sel instanceof StructuredSelection) {
					Object ps = ((StructuredSelection) sel).getFirstElement();
					if (ps instanceof PairStrategy) {
						boolean confirmed = MessageDialog.openConfirm(
								shlPtlTrader, 
								"Manual Open Confirmation", 
								String.format("Are you sure to manually open LONG position on %s/%s?", ((PairStrategy) ps).getStock1(), ((PairStrategy) ps).getStock2())
						);
						if (confirmed) {
							// send event to close the pair
							bus.post(new OpenPositionRequest(((PairStrategy) ps).getUid(), OpenPositionRequest.DIRECTION_LONG));
						}
						
					}
				}
				
				
			}
		});
		btnPairOpenLong.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, true, false, 1, 1));
		btnPairOpenLong.setToolTipText("Manually opens LONG position; asks for confirmation");
		btnPairOpenLong.setBounds(0, 0, 75, 25);
		btnPairOpenLong.setText("Open Long");
		
		btnPairOpenShort = new Button(groupPairManagement, SWT.NONE);
		btnPairOpenShort.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				ISelection sel = tableViewerPortfPairs.getSelection();
				if (!sel.isEmpty() && sel instanceof StructuredSelection) {
					Object ps = ((StructuredSelection) sel).getFirstElement();
					if (ps instanceof PairStrategy) {
						boolean confirmed = MessageDialog.openConfirm(
								shlPtlTrader, 
								"Manual Open Confirmation", 
								String.format("Are you sure to manually open SHORT position on %s/%s?", ((PairStrategy) ps).getStock1(), ((PairStrategy) ps).getStock2())
						);
						if (confirmed) {
							// send event to close the pair
							bus.post(new OpenPositionRequest(((PairStrategy) ps).getUid(), OpenPositionRequest.DIRECTION_SHORT));
						}
						
					}
				}
				
				
			}
		});
		btnPairOpenShort.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, true, false, 1, 1));
		btnPairOpenShort.setToolTipText("Manually opens SHORT position; asks for confirmation");
		btnPairOpenShort.setBounds(0, 0, 75, 25);
		btnPairOpenShort.setText("Open Short");
		
		
		
		
		
		btnPairClose = new Button(groupPairManagement, SWT.NONE);
		btnPairClose.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				ISelection sel = tableViewerPortfPairs.getSelection();
				if (!sel.isEmpty() && sel instanceof StructuredSelection) {
					Object ps = ((StructuredSelection) sel).getFirstElement();
					if (ps instanceof PairStrategy) {
						boolean confirmed = MessageDialog.openConfirm(
								shlPtlTrader, 
								"Manual Close Confirmation", 
								String.format("Are you sure to manually close pair position %s/%s?", ((PairStrategy) ps).getStock1(), ((PairStrategy) ps).getStock2())
						);
						if (confirmed) {
							// disable opening new positions
							((PairStrategy) ps).setTradingStatus(PairStrategy.TRADING_STATUS_MAINTAIN);
							// send event to close the pair
							bus.post(new ClosePositionRequest(((PairStrategy) ps).getUid()));
						}
						
					}
				}
				
				
			}
		});
		btnPairClose.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, true, false, 1, 1));
		btnPairClose.setToolTipText("Manually closes existing position if exits; asks for confirmation");
		btnPairClose.setBounds(0, 0, 75, 25);
		btnPairClose.setText("Close Position");
		
		
		// delete button
		btnPairDelete = new Button(groupPairManagement, SWT.NONE);
		btnPairDelete.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				ISelection sel = tableViewerPortfPairs.getSelection();
				if (!sel.isEmpty() && sel instanceof StructuredSelection) {
					Object ps = ((StructuredSelection) sel).getFirstElement();
					if (ps instanceof PairStrategy) {
						boolean confirmed = MessageDialog.openConfirm(
								shlPtlTrader, 
								"Delete Pair Confirmation", 
								String.format("Are you sure to delete pair %s/%s from portfolio?", ((PairStrategy) ps).getStock1(), ((PairStrategy) ps).getStock2())
						);
						if (confirmed) {
							tableViewerPortfPairs.setSelection(StructuredSelection.EMPTY);
							String logentry = String.format("deleting pair %s/%s", ((PairStrategy) ps).getStock1(), ((PairStrategy) ps).getStock2());
							logger.info(logentry);
							bus.post(new LogEvent(logentry));
							
							// prepare pair to be deleted
							((PairStrategy) ps).prepareToDelete();
							// delete pair
							((PairStrategy) ps).getPortfolio().removePairStrategy((PairStrategy) ps);
							// call webservice to delete pair (sync with server)
							apiClient.deletePairStrategy((PairStrategy) ps);
							tableViewerPortfPairs.refresh();
						}
						
					}
				}
				
				
			}
		});
		btnPairDelete.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, true, false, 1, 1));
		btnPairDelete.setToolTipText("Deletes pair from portfolio; asks for confirmation; only available if position is not openened");
		btnPairDelete.setBounds(0, 0, 75, 25);
		btnPairDelete.setText("Delete Pair");
		
		
		// resume button
		btnPairResume = new Button(groupPairManagement, SWT.NONE);
		btnPairResume.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				ISelection sel = tableViewerPortfPairs.getSelection();
				if (!sel.isEmpty() && sel instanceof StructuredSelection) {
					Object ps = ((StructuredSelection) sel).getFirstElement();
					if (ps instanceof PairStrategy) {
						boolean confirmed = MessageDialog.openConfirm(
								shlPtlTrader, 
								"Resume Trading Confirmation", 
								String.format("Are you sure to resume automated trading for pair %s/%s?", ((PairStrategy) ps).getStock1(), ((PairStrategy) ps).getStock2())
						);
						if (confirmed) {
							String logentry = String.format("resuming automated trading for %s/%s", ((PairStrategy) ps).getStock1(), ((PairStrategy) ps).getStock2());
							bus.post(new LogEvent(logentry));
							bus.post(new ResumeRequest(DateTime.now(), ((PairStrategy) ps).getUid()));
							
						}
						
					}
				}
			}
		});
		btnPairResume.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, true, false, 1, 1));
		btnPairResume.setToolTipText("Attempts to resume automated trading of the pair after manual intervention");
		btnPairResume.setBounds(0, 0, 75, 25);
		btnPairResume.setText("Resume Trading");

		// reset last opened button
		btnPairResetLastOpened = new Button(groupPairManagement, SWT.NONE);
		btnPairResetLastOpened.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				ISelection sel = tableViewerPortfPairs.getSelection();
				if (!sel.isEmpty() && sel instanceof StructuredSelection) {
					Object ps = ((StructuredSelection) sel).getFirstElement();
					if (ps instanceof PairStrategy) {
						boolean confirmed = MessageDialog.openConfirm(
								shlPtlTrader,
								"Reset Last Opened Confirmation",
								String.format("Are you sure to reset the Last Opened indicator for pair %s/%s?", ((PairStrategy) ps).getStock1(), ((PairStrategy) ps).getStock2())
						);
						if (confirmed) {
							String logentry = String.format("resetting Last Opened for %s/%s", ((PairStrategy) ps).getStock1(), ((PairStrategy) ps).getStock2());
							bus.post(new LogEvent(logentry));
                            ((PairStrategy) ps).setLastOpened(DateTime.now());
                            bus.post(new PairStateUpdated((PairStrategy) ps, DateTime.now()));

						}

					}
				}
			}
		});
		btnPairResetLastOpened.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, true, false, 1, 1));
		btnPairResetLastOpened.setToolTipText("Resets the Last Opened indicator (used in Max Days rule and in PDT protection)");
		btnPairResetLastOpened.setBounds(0, 0, 75, 25);
		btnPairResetLastOpened.setText("Reset Last Opened");

		
		TabFolder tabFolderMisc = new TabFolder(sashForm1, SWT.NONE);
		
		TabItem tbtmMiscLog = new TabItem(tabFolderMisc, SWT.NONE);
		tbtmMiscLog.setText("Log");
		
		Composite compositeLog = new Composite(tabFolderMisc, SWT.NONE);
		tbtmMiscLog.setControl(compositeLog);
		compositeLog.setLayout(new FillLayout(SWT.HORIZONTAL));
		
		tableViewerLog = new TableViewer(compositeLog, SWT.BORDER | SWT.FULL_SELECTION);
		tableLog = tableViewerLog.getTable();
		tableLog.setLinesVisible(true);
		tableLog.setHeaderVisible(true);
		
		TableColumn tblclmnLogDatetime = new TableColumn(tableLog, SWT.NONE);
		tblclmnLogDatetime.setWidth(140);
		tblclmnLogDatetime.setText("Date/Time");
		
		TableColumn tblclmnLogMessage = new TableColumn(tableLog, SWT.NONE);
		tblclmnLogMessage.setWidth(600);
		tblclmnLogMessage.setText("Message");
		
		TabItem tbtmMiscAccounts = new TabItem(tabFolderMisc, SWT.NONE);
		tbtmMiscAccounts.setText("Accounts");
		
		Composite compositeAccounts = new Composite(tabFolderMisc, SWT.NONE);
		tbtmMiscAccounts.setControl(compositeAccounts);
		compositeAccounts.setLayout(new FillLayout(SWT.HORIZONTAL));
		
		tableViewerAccounts = new TableViewer(compositeAccounts, SWT.BORDER | SWT.FULL_SELECTION);
		tableAccounts = tableViewerAccounts.getTable();
		tableAccounts.setLinesVisible(true);
		tableAccounts.setHeaderVisible(true);
		
		TableColumn tblclmnAccountsCode = new TableColumn(tableAccounts, SWT.NONE);
		tblclmnAccountsCode.setWidth(90);
		tblclmnAccountsCode.setText("Account");
		
		TableColumn tblclmnAccountsCurrency = new TableColumn(tableAccounts, SWT.NONE);
		tblclmnAccountsCurrency.setWidth(60);
		tblclmnAccountsCurrency.setText("Currency");
		
		TableColumn tblclmnAccountsEquity = new TableColumn(tableAccounts, SWT.RIGHT);
		tblclmnAccountsEquity.setWidth(80);
		tblclmnAccountsEquity.setText("Equity");
		
		TableColumn tblclmnAccountsBuyingPower = new TableColumn(tableAccounts, SWT.RIGHT);
		tblclmnAccountsBuyingPower.setWidth(90);
		tblclmnAccountsBuyingPower.setText("Buying Power");
		
		TableColumn tblclmnAccountsTotalCash = new TableColumn(tableAccounts, SWT.RIGHT);
		tblclmnAccountsTotalCash.setWidth(80);
		tblclmnAccountsTotalCash.setText("Total Cash");
		
		TableColumn tblclmnAccountsInitMargin = new TableColumn(tableAccounts, SWT.RIGHT);
		tblclmnAccountsInitMargin.setWidth(80);
		tblclmnAccountsInitMargin.setText("Init. Margin");
		
		TableColumn tblclmnAccountsMaintMargin = new TableColumn(tableAccounts, SWT.RIGHT);
		tblclmnAccountsMaintMargin.setWidth(90);
		tblclmnAccountsMaintMargin.setText("Maint. Margin");
		
		TableColumn tblclmnAccountsPl = new TableColumn(tableAccounts, SWT.RIGHT);
		tblclmnAccountsPl.setWidth(80);
		tblclmnAccountsPl.setText("P/L");
		
		TableColumn tblclmnAccountsAvailableFunds = new TableColumn(tableAccounts, SWT.RIGHT);
		tblclmnAccountsAvailableFunds.setWidth(100);
		tblclmnAccountsAvailableFunds.setText("Available Funds");
		
		TabItem tbtmMiscHistory = new TabItem(tabFolderMisc, SWT.NONE);
		tbtmMiscHistory.setText("Pair Trade History");
		
		Composite compositeHistory = new Composite(tabFolderMisc, SWT.NONE);
		tbtmMiscHistory.setControl(compositeHistory);
		compositeHistory.setLayout(new FillLayout(SWT.HORIZONTAL));
		
		tableViewerHistory = new TableViewer(compositeHistory, SWT.BORDER | SWT.FULL_SELECTION);
		tableHistory = tableViewerHistory.getTable();
		tableHistory.setLinesVisible(true);
		tableHistory.setHeaderVisible(true);
		
		TableColumn tblclmnHistDateTime = new TableColumn(tableHistory, SWT.NONE);
		tblclmnHistDateTime.setWidth(140);
		tblclmnHistDateTime.setText("Date, Time");
		
		TableColumn tblclmnHistAccount = new TableColumn(tableHistory, SWT.NONE);
		tblclmnHistAccount.setWidth(80);
		tblclmnHistAccount.setText("Account");
		
		TableColumn tblclmnHistStock1 = new TableColumn(tableHistory, SWT.NONE);
		tblclmnHistStock1.setWidth(100);
		tblclmnHistStock1.setText("Stock 1");
		
		TableColumn tblclmnHistStock2 = new TableColumn(tableHistory, SWT.NONE);
		tblclmnHistStock2.setWidth(100);
		tblclmnHistStock2.setText("Stock 2");
		
		TableColumn tblclmnHistAction = new TableColumn(tableHistory, SWT.NONE);
		tblclmnHistAction.setWidth(100);
		tblclmnHistAction.setText("Action");
		
		TableColumn tblclmnHistRealizedPL = new TableColumn(tableHistory, SWT.RIGHT);
		tblclmnHistRealizedPL.setWidth(100);
		tblclmnHistRealizedPL.setText("Realized P/L");
		
		TableColumn tblclmnHistRealizedPLPerc = new TableColumn(tableHistory, SWT.RIGHT);
		tblclmnHistRealizedPLPerc.setWidth(100);
		tblclmnHistRealizedPLPerc.setText("Realized P/L %");
		
		TableColumn tblclmnHistCommissions = new TableColumn(tableHistory, SWT.RIGHT);
		tblclmnHistCommissions.setWidth(100);
		tblclmnHistCommissions.setText("Commissions");
		
		TableColumn tblclmnHistZScore = new TableColumn(tableHistory, SWT.RIGHT);
		tblclmnHistZScore.setWidth(100);
		tblclmnHistZScore.setText("Z-Score");
		
		TableColumn tblclmnHistComment = new TableColumn(tableHistory, SWT.NONE);
		tblclmnHistComment.setWidth(250);
		tblclmnHistComment.setText("Comment");
		
		TabItem tbtmMiscLegHistory = new TabItem(tabFolderMisc, SWT.NONE);
		tbtmMiscLegHistory.setText("Trade History");
		
		Composite compositeLegHistory = new Composite(tabFolderMisc, SWT.NONE);
		tbtmMiscLegHistory.setControl(compositeLegHistory);
		compositeLegHistory.setLayout(new FillLayout(SWT.HORIZONTAL));
		
		tableViewerLegHistory = new TableViewer(compositeLegHistory, SWT.BORDER | SWT.FULL_SELECTION);
		tableLegHistory = tableViewerLegHistory.getTable();
		tableLegHistory.setLinesVisible(true);
		tableLegHistory.setHeaderVisible(true);
		
		TableColumn tblclmnLegHistDatetime = new TableColumn(tableLegHistory, SWT.NONE);
		tblclmnLegHistDatetime.setWidth(140);
		tblclmnLegHistDatetime.setText("Date, Time");
		
		TableColumn tblclmnLegHistAccount = new TableColumn(tableLegHistory, SWT.NONE);
		tblclmnLegHistAccount.setWidth(80);
		tblclmnLegHistAccount.setText("Account");
		
		TableColumn tblclmnLegHistSymbol = new TableColumn(tableLegHistory, SWT.NONE);
		tblclmnLegHistSymbol.setWidth(100);
		tblclmnLegHistSymbol.setText("Symbol");
		
		TableColumn tblclmnLegHistAction = new TableColumn(tableLegHistory, SWT.NONE);
		tblclmnLegHistAction.setWidth(100);
		tblclmnLegHistAction.setText("Action");
		
		TableColumn tblclmnLegHistRealizedPl = new TableColumn(tableLegHistory, SWT.RIGHT);
		tblclmnLegHistRealizedPl.setWidth(100);
		tblclmnLegHistRealizedPl.setText("Realized P/L");
		
		TableColumn tblclmnLegHistQty = new TableColumn(tableLegHistory, SWT.RIGHT);
		tblclmnLegHistQty.setWidth(100);
		tblclmnLegHistQty.setText("Qty");
		
		TableColumn tblclmnLegPrice = new TableColumn(tableLegHistory, SWT.RIGHT);
		tblclmnLegPrice.setWidth(100);
		tblclmnLegPrice.setText("Price");
		
		TableColumn tblclmnLegHistValue = new TableColumn(tableLegHistory, SWT.RIGHT);
		tblclmnLegHistValue.setWidth(100);
		tblclmnLegHistValue.setText("Value");
		
		TableColumn tblclmnLegHistCommissions = new TableColumn(tableLegHistory, SWT.RIGHT);
		tblclmnLegHistCommissions.setWidth(100);
		tblclmnLegHistCommissions.setText("Commissions");
		
		TableColumn tblclmnLegHistFillTime = new TableColumn(tableLegHistory, SWT.RIGHT);
		tblclmnLegHistFillTime.setWidth(100);
		tblclmnLegHistFillTime.setText("Fill Time");
		
		TableColumn tblclmnLegHistSlippage = new TableColumn(tableLegHistory, SWT.RIGHT);
		tblclmnLegHistSlippage.setWidth(100);
		tblclmnLegHistSlippage.setText("Slippage");
		
		TabItem tbtmMiscSettings = new TabItem(tabFolderMisc, SWT.NONE);
		tbtmMiscSettings.setText("Settings");
		
		Composite compositeMiscSettings = new Composite(tabFolderMisc, SWT.NONE);
		compositeMiscSettings.setBackground(SWTResourceManager.getColor(SWT.COLOR_WIDGET_BACKGROUND));
		tbtmMiscSettings.setControl(compositeMiscSettings);
		compositeMiscSettings.setLayout(new GridLayout(3, false));
		
		Group grpPairTradingLab = new Group(compositeMiscSettings, SWT.NONE);
		grpPairTradingLab.setBackground(SWTResourceManager.getColor(SWT.COLOR_WIDGET_BACKGROUND));
		grpPairTradingLab.setLayout(new GridLayout(2, false));
		grpPairTradingLab.setLayoutData(new GridData(SWT.LEFT, SWT.FILL, false, true, 1, 1));
		grpPairTradingLab.setText("Pair Trading Lab Authentication");
		
		Label lblPTLUsername = new Label(grpPairTradingLab, SWT.NONE);
		lblPTLUsername.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false, 1, 1));
		lblPTLUsername.setText("Access Key:");
		
		textPTLAccessKey = new Text(grpPairTradingLab, SWT.BORDER);
		GridData gd_textPTLAccessKey = new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1);
		gd_textPTLAccessKey.widthHint = 120;
		textPTLAccessKey.setLayoutData(gd_textPTLAccessKey);
		
		Label lblPTLSecretKey = new Label(grpPairTradingLab, SWT.NONE);
		lblPTLSecretKey.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false, 1, 1));
		lblPTLSecretKey.setText("Secret Key:");
		
		textPTLAccessToken = new Text(grpPairTradingLab, SWT.BORDER | SWT.PASSWORD);
		GridData gd_textPTLAccessToken = new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1);
		gd_textPTLAccessToken.widthHint = 120;
		textPTLAccessToken.setLayoutData(gd_textPTLAccessToken);
		
		btnPTLSaveToken = new Button(grpPairTradingLab, SWT.CHECK | SWT.CENTER);
		btnPTLSaveToken.setText("Store secret key");
		
		btnPTLConnect = new Button(grpPairTradingLab, SWT.CENTER);
		btnPTLConnect.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				connectToPtl();
				
			}
		});
		btnPTLConnect.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1));
		btnPTLConnect.setText("Connect");
		
		Group grpInteractiveBrokers = new Group(compositeMiscSettings, SWT.NONE);
		grpInteractiveBrokers.setBackground(SWTResourceManager.getColor(SWT.COLOR_WIDGET_BACKGROUND));
		grpInteractiveBrokers.setLayout(new GridLayout(2, false));
		grpInteractiveBrokers.setLayoutData(new GridData(SWT.LEFT, SWT.FILL, false, true, 1, 1));
		grpInteractiveBrokers.setText("Interactive Brokers API");
		
		Label lblIbClientId = new Label(grpInteractiveBrokers, SWT.NONE);
		lblIbClientId.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false, 1, 1));
		lblIbClientId.setText("Client Id:");
		
		textIbClientId = new Text(grpInteractiveBrokers, SWT.BORDER);
		textIbClientId.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1));
		
		Label lblIbHost = new Label(grpInteractiveBrokers, SWT.NONE);
		lblIbHost.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false, 1, 1));
		lblIbHost.setBounds(0, 0, 55, 15);
		lblIbHost.setText("Host:");
		
		textIbHost = new Text(grpInteractiveBrokers, SWT.BORDER);
		textIbHost.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1));
		textIbHost.setBounds(0, 0, 76, 21);
		
		Label lblIbPort = new Label(grpInteractiveBrokers, SWT.NONE);
		lblIbPort.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false, 1, 1));
		lblIbPort.setBounds(0, 0, 55, 15);
		lblIbPort.setText("Port:");
		
		textIbPort = new Text(grpInteractiveBrokers, SWT.BORDER);
		textIbPort.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1));
		textIbPort.setBounds(0, 0, 76, 21);
		
		
		Label lblIbFaAccount = new Label(grpInteractiveBrokers, SWT.NONE);
		lblIbFaAccount.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false, 1, 1));
		lblIbFaAccount.setBounds(0, 0, 55, 15);
		lblIbFaAccount.setText("FA Account:");
		
		textIbFaAccount = new Text(grpInteractiveBrokers, SWT.BORDER);
		textIbFaAccount.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1));
		textIbFaAccount.setBounds(0, 0, 76, 21);
		
		btnIbConnect = new Button(grpInteractiveBrokers, SWT.NONE);
		btnIbConnect.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				connectToIb();
			}
		});
		btnIbConnect.setBounds(0, 0, 75, 25);
		btnIbConnect.setText("Connect");
		new Label(grpInteractiveBrokers, SWT.NONE);
		
		Group grpSpecialSettings = new Group(compositeMiscSettings, SWT.NONE);
		grpSpecialSettings.setBackground(SWTResourceManager.getColor(SWT.COLOR_WIDGET_BACKGROUND));
		grpSpecialSettings.setLayout(new GridLayout(1, false));
		grpSpecialSettings.setLayoutData(new GridData(SWT.LEFT, SWT.FILL, false, true, 1, 1));
		grpSpecialSettings.setText("Special Settings");
		
		btnConfidentialMode = new Button(grpSpecialSettings, SWT.CHECK | SWT.CENTER);
		btnConfidentialMode.setText("Enable Confidential Mode");
		btnConfidentialMode.setToolTipText("Use Confidential Mode if you do not want the application\nto send any trade statistics out to Pair Trading Lab website.");
		Label lblConfidentialWarning = new Label(grpSpecialSettings, SWT.NONE);
		lblConfidentialWarning.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false, 1, 1));
		lblConfidentialWarning.setBounds(0, 0, 55, 15);
		lblConfidentialWarning.setText("Warning: When using the confidential mode,\nonline reports/statistics do not work.");
		sashForm1.setWeights(new int[] {3, 2, 3});
		
		final CoolBar coolBar = new CoolBar(shlPtlTrader, SWT.NONE);
		coolBar.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));

		// PTL status (indicator + label + text)
		CoolItem ptlStatusItem = new CoolItem(coolBar,SWT.NONE);

		Composite composite = new Composite(coolBar, SWT.NONE);
		composite.setLayout(new GridLayout(3, false));

		lblPTLStatusLed = new Label(composite, SWT.HORIZONTAL);
		lblPTLStatusLed.setImage(null);
		GridData gd_led_1 = new GridData ();
		gd_led_1.widthHint = 16;
		gd_led_1.heightHint = 16;
		lblPTLStatusLed.setLayoutData(gd_led_1);
		lblPTLStatusLed.pack();
		
		Label lblPTLStatus = new Label(composite, SWT.NONE);
		lblPTLStatus.setImage(null);
		lblPTLStatus.setText("PairTradingLab Status:");
		lblPTLStatus.pack();

		textPtlStatus = new Text(composite, SWT.READ_ONLY);
		GridData gd_ptl_status = new GridData ();
		gd_ptl_status.widthHint = 120;
		textPtlStatus.setLayoutData(gd_ptl_status);
		textPtlStatus.pack();

		composite.pack();
		Point size = composite.getSize();
		ptlStatusItem.setControl(composite);
		ptlStatusItem.setSize(ptlStatusItem.computeSize(size.x, size.y));

		// IB status (indicator + label + text)
		CoolItem ibStatusItem = new CoolItem(coolBar, SWT.NONE);

		Composite composite2 = new Composite(coolBar, SWT.NONE);
		composite2.setLayout(new GridLayout(3, false));

		lblIbStatusLed = new Label(composite2, SWT.NONE);
		GridData gd_led_2 = new GridData ();
		gd_led_2.widthHint = 16;
		gd_led_2.heightHint = 16;
		lblIbStatusLed.setLayoutData(gd_led_2);
		lblIbStatusLed.setImage(null);
		lblIbStatusLed.pack();

		Label lblIbStatus = new Label(composite2, SWT.NONE);
		lblIbStatus.setText("IB Status:");
		lblIbStatus.pack();

		textIbStatus = new Text(composite2, SWT.READ_ONLY);
		GridData gd_ib_status = new GridData ();
		gd_ib_status.widthHint = 120;
		textIbStatus.setLayoutData(gd_ib_status);
		textIbStatus.pack();

		composite2.pack();
		Point size2 = composite2.getSize();
		ibStatusItem.setControl(composite2);
		ibStatusItem.setSize(ibStatusItem.computeSize(size2.x, size2.y));

		// application menu
		Menu menu = new Menu(shlPtlTrader, SWT.BAR);
		shlPtlTrader.setMenuBar(menu);
		
		MenuItem mntmFile = new MenuItem(menu, SWT.CASCADE);
		mntmFile.setText("File");
		
		Menu menuFile = new Menu(mntmFile);
		mntmFile.setMenu(menuFile);
		
		mntmReloadPortfolios = new MenuItem(menuFile, SWT.NONE);
		mntmReloadPortfolios.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				apiClient.loadPortfolios(true);
			}
		});
		mntmReloadPortfolios.setText("Update Portfolios From PTL");
		
		MenuItem mntmExit = new MenuItem(menuFile, SWT.NONE);
		mntmExit.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				boolean confirmed = MessageDialog.openConfirm(shlPtlTrader, "Confirm Operation", "Are you sure to exit PTL Trader?");
				if (confirmed) shlPtlTrader.close();
			}
		});
		mntmExit.setText("Exit");
		
		MenuItem mntmHelp = new MenuItem(menu, SWT.CASCADE);
		mntmHelp.setText("Help");
		
		Menu menuHelp = new Menu(mntmHelp);
		mntmHelp.setMenu(menuHelp);
		
		MenuItem mntmAbout = new MenuItem(menuHelp, SWT.NONE);
		mntmAbout.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				aboutDialog.open();
			}
		});
		mntmAbout.setText("About");
		m_bindingContext = initDataBindings();
		finishBindings();
	}

	public Status getmStatus() {
		return mStatus;
	}

	public PortfolioList getmPortfolioList() {
		return mPortfolioList;
	}

	public LogEntryList getmLogEntryList() {
		return mLogEntryList;
	}

	public TradeHistory getmTradeHistory() {
		return mTradeHistory;
	}

	public Settings getmSettings() {
		return mSettings;
	}

	public SimpleWrapper getIbWrapper() {
		return getWrapper();
	}
	
	public LegHistory getmLegHistory() {
		return mLegHistory;
	}
	
	public AccountList getmAccountList() {
		return mAccountList;
	}
	
	
	private void connectToIb() {
		bus.post(new LogEvent("Connecting to Interactive Brokers API"));
		mStatus.setIbConnecting(true);
		mStatus.setIbConnected(false);
		connectedAccounts.clear();
		getWrapper().ibConnectAsync();
			
	}
	
	@Subscribe
	public void onConnected(Connected c) {
		// called from any thread!
		if (!c.ibWrapperUid.equals(getWrapper().getUid())) return; // not ours!
		mStatus.setIbConnected(true);
		mStatus.setIbConnecting(false);
		bus.post(new LogEvent(String.format("Connected to IB, server version: %d",  getWrapper().getIbSocket().serverVersion())));
		// that's it
		
	}
	
	@Subscribe
	public void onIbConnectionFailed(final IbConnectionFailed e) {
		// called from any thread!
		if (!e.uid.equals(getWrapper().getUid())) return; // not ours!
		bus.post(new LogEvent(String.format("Failed to connect to IB API")));
		mStatus.setIbConnecting(false);
		mStatus.setIbConnected(false);
		
		if (e.reason==IbConnectionFailed.REASON_API_VERSION) {
			Display.getDefault().asyncExec(new Runnable() {
			    public void run() {
			    	MessageDialog.openError(shlPtlTrader, "IB API Version Error", "Please upgrade your IB API at least to version "+e.reasonData1+" (detected: "+e.reasonData2+")");
			    }
			});
			
		}
		
	}
	
	private void connectToPtl() {
		mSettings.setPtlConnectEnabled(false);
		apiClient.loadPortfolios(false);
		apiClient.loadTransactionHistories();
		apiClient.loadPairTradeHistories();
	}
	
	
	@Subscribe
	public void onPtlApiProblem(final PtlApiProblem p) {
		if ("loadPortfolios".equals(p.origin)) {
			mSettings.setPtlConnectEnabled(true);
			Display.getDefault().syncExec(new Runnable()
	        {
	            @Override
	            public void run()
	            {
	            	MessageDialog.openError(shlPtlTrader, "Pair Trading Lab Connection Error", p.error.toString());
	            }
	       });
			
		}
		
		if ("bindPortfolioToAccount".equals(p.origin)) {
			Display.getDefault().syncExec(new Runnable()
	        {
	            @Override
	            public void run()
	            {
	            	MessageDialog.openError(shlPtlTrader, "Bind Operation Failed", p.error.toString());
	            }
	       });
			
		}
	}
	
	@Subscribe
	public void onAmqpProblem(final AmqpProblem p) {
		mSettings.setPtlConnectEnabled(true);
		bus.post(new LogEvent(p.error.toString()));
		if (p.error==AmqpError.AUTH_FAIL) {
			Display.getDefault().syncExec(new Runnable()
	        {
	            @Override
	            public void run()
	            {
	            	MessageDialog.openError(shlPtlTrader, "Pair Trading Lab Event Bus Connection Error", 
	            			p.error.toString()+"\nYou can still use this application, but monitoring will not work and trade history/results are not being sent to Pair Trading Lab.");
	            }
	       });
		}
		
	
	}
	
	protected void finishBindings() {
		// put manually implemented bindings here (bindings which prevent the WindowBuilder to run)
		IObservableValue<Boolean> observeEnabledMntmReloadPortfolios = WidgetProperties.enabled().observe(mntmReloadPortfolios);
		IObservableValue ptlConnectedMStatusObserveValue = BeanProperties.value("ptlConnected").observe(mStatus);
		m_bindingContext.bindValue(observeEnabledMntmReloadPortfolios, ptlConnectedMStatusObserveValue, null, null);
		
		// bind slot usage bar progressBarPairSlotUsage, it has to use custom binding solution
		IObservableValue observeSingleSelectionTableViewerPortfolios_2 = ViewerProperties.singleSelection().observe(tableViewerPortfolios);
		IObservableValue tableViewerPortfoliosSlotUsageObserveDetailValue = BeanProperties.value(Portfolio.class, "slotUsage", Integer.class).observeDetail(observeSingleSelectionTableViewerPortfolios_2);
		m_bindingContext.bindValue(new ProgressBarObservableValue(progressBarPairSlotUsage, ProgressBarObservableValue.ATTR_SELECTION), tableViewerPortfoliosSlotUsageObserveDetailValue, null, null);
		
		// control decorators
		ControlDecorationSupport.create(textPortfNameBinding, SWT.TOP | SWT.LEFT);
		ControlDecorationSupport.create(textPairMargin1Binding, SWT.TOP | SWT.LEFT);
		ControlDecorationSupport.create(textPairMargin2Binding, SWT.TOP | SWT.LEFT);
		ControlDecorationSupport.create(textPairBasicSlotOccupationBinding, SWT.TOP | SWT.LEFT);
		
		ControlDecorationSupport.create(textPairXtraMinExpectBinding, SWT.TOP | SWT.LEFT);
		ControlDecorationSupport.create(textPairXtraMinPlBinding, SWT.TOP | SWT.LEFT);
		ControlDecorationSupport.create(textPairXtraMinPriceBinding, SWT.TOP | SWT.LEFT);
	
	
	}
	protected DataBindingContext initDataBindings() {
		DataBindingContext bindingContext = new DataBindingContext();
		//
		ObservableListContentProvider listContentProvider = new ObservableListContentProvider();
		IObservableMap[] observeMaps = BeansObservables.observeMaps(listContentProvider.getKnownElements(), Portfolio.class, new String[]{"name", "pairCount", "totalPlS", "accountCode"});
		tableViewerPortfolios.setLabelProvider(new ObservableMapLabelProvider(observeMaps));
		tableViewerPortfolios.setContentProvider(listContentProvider);
		//
		IObservableList portfoliosMPortfolioListObserveList = BeanProperties.list("portfolios").observe(mPortfolioList);
		tableViewerPortfolios.setInput(portfoliosMPortfolioListObserveList);
		//
		ObservableListContentProvider listContentProvider_1 = new ObservableListContentProvider();
		IObservableMap[] observeMaps_1 = BeansObservables.observeMaps(listContentProvider_1.getKnownElements(), PairStrategy.class, new String[]{"stockDisp1", "stockDisp2", "model", "status", "coreStatus", "plS", "zscoreBidS", "zscoreAskS", "rsiS", "profitPotentialS", "lastOpenedS", "daysRemaining"});
		tableViewerPortfPairs.setLabelProvider(new ObservableMapLabelProvider(observeMaps_1));
		tableViewerPortfPairs.setContentProvider(listContentProvider_1);
		//
		IObservableValue observeSingleSelectionTableViewerPortfolios = ViewerProperties.singleSelection().observe(tableViewerPortfolios);
		IObservableList tableViewerPortfoliosPairsObserveDetailList = BeanProperties.list(Portfolio.class, "pairStrategies", PairStrategy.class).observeDetail(observeSingleSelectionTableViewerPortfolios);
		tableViewerPortfPairs.setInput(tableViewerPortfoliosPairsObserveDetailList);
		//
		ObservableListContentProvider listContentProvider_2 = new ObservableListContentProvider();
		IObservableMap[] observeMaps_2 = BeansObservables.observeMaps(listContentProvider_2.getKnownElements(), Position.class, new String[]{"symbol", "secType", "last", "bid", "ask", "bidSize", "askSize", "status", "qty", "plS", "avgOpenPriceS", "value", "shortable"});
		tableViewerPairPositionList.setLabelProvider(new ObservableMapLabelProvider(observeMaps_2));
		tableViewerPairPositionList.setContentProvider(listContentProvider_2);
		//
		IObservableValue observeSingleSelectionTableViewerPortfPairs = ViewerProperties.singleSelection().observe(tableViewerPortfPairs);
		IObservableList tableViewerPortfPairsPositionsObserveDetailList = BeanProperties.list(PairStrategy.class, "positions", Position.class).observeDetail(observeSingleSelectionTableViewerPortfPairs);
		tableViewerPairPositionList.setInput(tableViewerPortfPairsPositionsObserveDetailList);
		//
		IObservableValue<String> observeTextTextIbClientIdObserveWidget = WidgetProperties.text(SWT.Modify).observeDelayed(300, textIbClientId);
		IObservableValue ibClientIdMSettingsObserveValue = BeanProperties.value("ibClientId").observe(getWrapper());
		bindingContext.bindValue(observeTextTextIbClientIdObserveWidget, ibClientIdMSettingsObserveValue, null, null);
		//
		IObservableValue<String> observeTextTextIbHostObserveWidget = WidgetProperties.text(SWT.Modify).observeDelayed(300, textIbHost);
		IObservableValue ibHostMSettingsObserveValue = BeanProperties.value("ibHost").observe(getWrapper());
		bindingContext.bindValue(observeTextTextIbHostObserveWidget, ibHostMSettingsObserveValue, null, null);
		//
		IObservableValue<String> observeTextTextIbPortObserveWidget = WidgetProperties.text(SWT.Modify).observeDelayed(300, textIbPort);
		IObservableValue ibPortMSettingsObserveValue = BeanProperties.value("ibPort").observe(getWrapper());
		UpdateValueStrategy strategy_6 = new UpdateValueStrategy();
		strategy_6.setConverter(new SimpleInt2String());
		bindingContext.bindValue(observeTextTextIbPortObserveWidget, ibPortMSettingsObserveValue, null, strategy_6);
		//
		IObservableValue<String> observeTextTextIbFaAccountObserveWidget = WidgetProperties.text(SWT.Modify).observeDelayed(300, textIbFaAccount);
		IObservableValue ibFaAccountMSettingsObserveValue = BeanProperties.value("ibFaAccount").observe(getWrapper());
		bindingContext.bindValue(observeTextTextIbFaAccountObserveWidget, ibFaAccountMSettingsObserveValue, null, null);
		//
		IObservableValue<String> observeTextTextIbStatusObserveWidget = WidgetProperties.text(SWT.Modify).observe(textIbStatus);
		IObservableValue ibConnectedMStatusObserveValue = BeanProperties.value("ibConnected").observe(mStatus);
		bindingContext.bindValue(observeTextTextIbStatusObserveWidget, ibConnectedMStatusObserveValue, UpdateValueStrategy.never(), UpdateValueStrategy.create(new IbConnected2String()));
		//
		IObservableValue observeEnabledBtnIbConnectObserveWidget = WidgetProperties.enabled().observe(btnIbConnect);
		IObservableValue ibConnectingOrConnectedMStatusObserveValue = BeanProperties.value("ibConnectingOrConnected").observe(mStatus);
		UpdateValueStrategy strategy_1 = new UpdateValueStrategy();
		strategy_1.setConverter(new Inverter());
		bindingContext.bindValue(observeEnabledBtnIbConnectObserveWidget, ibConnectingOrConnectedMStatusObserveValue, null, strategy_1);
		// start manually entered
		//
		IObservableValue<Boolean> observeEnabledTextIbHostObserveWidget = WidgetProperties.enabled().observe(textIbHost);
		bindingContext.bindValue(observeEnabledTextIbHostObserveWidget, ibConnectingOrConnectedMStatusObserveValue, null, strategy_1);
		//
		IObservableValue observeEnabledTextIbPortObserveWidget = WidgetProperties.enabled().observe(textIbPort);
		bindingContext.bindValue(observeEnabledTextIbPortObserveWidget, ibConnectingOrConnectedMStatusObserveValue, null, strategy_1);
		//
		IObservableValue observeEnabledTextIbClientIdObserveWidget = WidgetProperties.enabled().observe(textIbClientId);
		bindingContext.bindValue(observeEnabledTextIbClientIdObserveWidget, ibConnectingOrConnectedMStatusObserveValue, null, strategy_1);
		//
		IObservableValue observeEnabledTextIbFaAccountObserveWidget = WidgetProperties.enabled().observe(textIbFaAccount);
		bindingContext.bindValue(observeEnabledTextIbFaAccountObserveWidget, ibConnectingOrConnectedMStatusObserveValue, null, strategy_1);
		// end manually entered
		//
		ObservableListContentProvider listContentProvider_3 = new ObservableListContentProvider();
		IObservableMap[] observeMaps_3 = BeansObservables.observeMaps(listContentProvider_3.getKnownElements(), LogEntry.class, new String[]{"datetimeFormatted", "message"});
		tableViewerLog.setLabelProvider(new ObservableMapLabelProvider(observeMaps_3));
		tableViewerLog.setContentProvider(listContentProvider_3);
		//
		IObservableList entriesMLogEntryListObserveList = BeanProperties.list("entries").observe(mLogEntryList);
		tableViewerLog.setInput(entriesMLogEntryListObserveList);
		//
		ObservableListContentProvider listContentProvider_4 = new ObservableListContentProvider();
		IObservableMap[] observeMaps_4 = BeansObservables.observeMaps(listContentProvider_4.getKnownElements(), TradeHistoryEntry.class, new String[]{"datetimeS", "account", "stock1", "stock2", "action", "realizedPlS", "realizedPlPercentS", "commissionsS", "zscoreS", "comment"});
		tableViewerHistory.setLabelProvider(new ObservableMapLabelProvider(observeMaps_4));
		tableViewerHistory.setContentProvider(listContentProvider_4);
		//
		IObservableList entriesMTradeHistoryObserveList = BeanProperties.list("entries").observe(mTradeHistory);
		tableViewerHistory.setInput(entriesMTradeHistoryObserveList);
		//
		IObservableValue<Image> observeImageLblIbStatusLedObserveWidget = WidgetProperties.image().observe(lblIbStatusLed);
		UpdateValueStrategy strategy_2 = new UpdateValueStrategy();
		strategy_2.setConverter(new Boolean2Led());
		bindingContext.bindValue(observeImageLblIbStatusLedObserveWidget, ibConnectedMStatusObserveValue, null, strategy_2);
		//
		IObservableValue<Image> observeImageLblPTLStatusLedObserveWidget = WidgetProperties.image().observe(lblPTLStatusLed);
		IObservableValue ptlConnectedMStatusObserveValue = BeanProperties.value("ptlConnected").observe(mStatus);
		UpdateValueStrategy strategy_3 = new UpdateValueStrategy();
		UpdateValueStrategy strategy_5 = new UpdateValueStrategy();
		strategy_5.setConverter(new Boolean2Led());
		bindingContext.bindValue(observeImageLblPTLStatusLedObserveWidget, ptlConnectedMStatusObserveValue, strategy_3, strategy_5);
		//
		IObservableValue<String> observeTextTextPtlStatusObserveWidget = WidgetProperties.text(SWT.Modify).observe(textPtlStatus);
		bindingContext.bindValue(observeTextTextPtlStatusObserveWidget, ptlConnectedMStatusObserveValue, UpdateValueStrategy.never(), UpdateValueStrategy.create(new IbConnected2String()));
		//
		IObservableValue<String> observeTextTextPairBasicStock1ObserveWidget = WidgetProperties.text(SWT.Modify).observe(textPairBasicStock1);
		IObservableValue observeSingleSelectionTableViewerPortfPairs_1 = ViewerProperties.singleSelection().observe(tableViewerPortfPairs);
		IObservableValue tableViewerPortfPairsStock1ObserveDetailValue = BeanProperties.value(PairStrategy.class, "stock1", String.class).observeDetail(observeSingleSelectionTableViewerPortfPairs_1);
		bindingContext.bindValue(observeTextTextPairBasicStock1ObserveWidget, tableViewerPortfPairsStock1ObserveDetailValue, null, null);
		//
		IObservableValue<String> observeTextTextPairBasicStock2ObserveWidget = WidgetProperties.text(SWT.Modify).observe(textPairBasicStock2);
		IObservableValue observeSingleSelectionTableViewerPortfPairs_2 = ViewerProperties.singleSelection().observe(tableViewerPortfPairs);
		IObservableValue tableViewerPortfPairsStock2ObserveDetailValue = BeanProperties.value(PairStrategy.class, "stock2", String.class).observeDetail(observeSingleSelectionTableViewerPortfPairs_2);
		bindingContext.bindValue(observeTextTextPairBasicStock2ObserveWidget, tableViewerPortfPairsStock2ObserveDetailValue, null, null);
		//
		IObservableValue<String> observeTextTextPairBasicModelObserveWidget = WidgetProperties.text(SWT.Modify).observe(textPairBasicModel);
		IObservableValue observeSingleSelectionTableViewerPortfPairs_3 = ViewerProperties.singleSelection().observe(tableViewerPortfPairs);
		IObservableValue tableViewerPortfPairsModelObserveDetailValue = BeanProperties.value(PairStrategy.class, "model", String.class).observeDetail(observeSingleSelectionTableViewerPortfPairs_3);
		bindingContext.bindValue(observeTextTextPairBasicModelObserveWidget, tableViewerPortfPairsModelObserveDetailValue, null, null);
		//
		IObservableValue<String> observeTextTextPairModelEntryThresholdObserveWidget = WidgetProperties.text(SWT.Modify).observe(textPairModelEntryThreshold);
		IObservableValue observeSingleSelectionTableViewerPortfPairs_4 = ViewerProperties.singleSelection().observe(tableViewerPortfPairs);
		IObservableValue tableViewerPortfPairsEntryThresholdObserveDetailValue = BeanProperties.value(PairStrategy.class, "entryThreshold", Double.class).observeDetail(observeSingleSelectionTableViewerPortfPairs_4);
		bindingContext.bindValue(observeTextTextPairModelEntryThresholdObserveWidget, tableViewerPortfPairsEntryThresholdObserveDetailValue, null, null);
		//
		IObservableValue<String> observeTextTextPairModelExitThresholdObserveWidget = WidgetProperties.text(SWT.Modify).observe(textPairModelExitThreshold);
		IObservableValue observeSingleSelectionTableViewerPortfPairs_5 = ViewerProperties.singleSelection().observe(tableViewerPortfPairs);
		IObservableValue tableViewerPortfPairsExitThresholdObserveDetailValue = BeanProperties.value(PairStrategy.class, "exitThreshold", Double.class).observeDetail(observeSingleSelectionTableViewerPortfPairs_5);
		bindingContext.bindValue(observeTextTextPairModelExitThresholdObserveWidget, tableViewerPortfPairsExitThresholdObserveDetailValue, null, null);
        //
        IObservableValue<String> observeTextTextPairModelDowntickThresholdObserveWidget = WidgetProperties.text(SWT.Modify).observe(textPairModelDowntickThreshold);
        IObservableValue observeSingleSelectionTableViewerPortfPairs_DowntickThreshold = ViewerProperties.singleSelection().observe(tableViewerPortfPairs);
        IObservableValue tableViewerPortfPairsDowntickThresholdObserveDetailValue = BeanProperties.value(PairStrategy.class, "downtickThreshold", Double.class).observeDetail(observeSingleSelectionTableViewerPortfPairs_DowntickThreshold);
        bindingContext.bindValue(observeTextTextPairModelDowntickThresholdObserveWidget, tableViewerPortfPairsDowntickThresholdObserveDetailValue, null, null);
		//
		IObservableValue<String> observeTextTextPairModelMaxEntryScoreObserveWidget = WidgetProperties.text(SWT.Modify).observe(textPairModelMaxEntryScore);
		IObservableValue observeSingleSelectionTableViewerPortfPairs_6 = ViewerProperties.singleSelection().observe(tableViewerPortfPairs);
		IObservableValue tableViewerPortfPairsMaxEntryScoreObserveDetailValue = BeanProperties.value(PairStrategy.class, "maxEntryScore", Double.class).observeDetail(observeSingleSelectionTableViewerPortfPairs_6);
		bindingContext.bindValue(observeTextTextPairModelMaxEntryScoreObserveWidget, tableViewerPortfPairsMaxEntryScoreObserveDetailValue, null, null);
		//
		IObservableValue<String> observeTextTextPairModelRatioMaTypeObserveWidget = WidgetProperties.text(SWT.Modify).observe(textPairModelRatioMaType);
		IObservableValue observeSingleSelectionTableViewerPortfPairs_7 = ViewerProperties.singleSelection().observe(tableViewerPortfPairs);
		IObservableValue tableViewerPortfPairsRatioMaTypeObserveDetailValue = BeanProperties.value(PairStrategy.class, "ratioMaType", MAType.class).observeDetail(observeSingleSelectionTableViewerPortfPairs_7);
		bindingContext.bindValue(observeTextTextPairModelRatioMaTypeObserveWidget, tableViewerPortfPairsRatioMaTypeObserveDetailValue, null, null);
		//
		IObservableValue<String> observeTextTextPairModelRatioMaPeriodObserveWidget = WidgetProperties.text(SWT.Modify).observe(textPairModelRatioMaPeriod);
		IObservableValue observeSingleSelectionTableViewerPortfPairs_8 = ViewerProperties.singleSelection().observe(tableViewerPortfPairs);
		IObservableValue tableViewerPortfPairsRatioMaPeriodObserveDetailValue = BeanProperties.value(PairStrategy.class, "ratioMaPeriod", Integer.class).observeDetail(observeSingleSelectionTableViewerPortfPairs_8);
		bindingContext.bindValue(observeTextTextPairModelRatioMaPeriodObserveWidget, tableViewerPortfPairsRatioMaPeriodObserveDetailValue, null, null);
		//
		IObservableValue<String> observeTextTextPairModelRatioStddevPeriodObserveWidget = WidgetProperties.text(SWT.Modify).observe(textPairModelRatioStddevPeriod);
		IObservableValue observeSingleSelectionTableViewerPortfPairs_9 = ViewerProperties.singleSelection().observe(tableViewerPortfPairs);
		IObservableValue tableViewerPortfPairsRatioStdDevPeriodObserveDetailValue = BeanProperties.value(PairStrategy.class, "ratioStdDevPeriod", Integer.class).observeDetail(observeSingleSelectionTableViewerPortfPairs_9);
		bindingContext.bindValue(observeTextTextPairModelRatioStddevPeriodObserveWidget, tableViewerPortfPairsRatioStdDevPeriodObserveDetailValue, null, null);
		// (created manually)
		IObservableValue<String> observeTextTextPairModelRatioRsiPeriodObserveWidget = WidgetProperties.text(SWT.Modify).observe(textPairModelRatioRsiPeriod);
		IObservableValue tableViewerPortfPairsRatioRsiPeriodObserveDetailValue = BeanProperties.value(PairStrategy.class, "ratioRsiPeriod", Integer.class).observeDetail(observeSingleSelectionTableViewerPortfPairs_9);
		bindingContext.bindValue(observeTextTextPairModelRatioRsiPeriodObserveWidget, tableViewerPortfPairsRatioRsiPeriodObserveDetailValue, null, null);
		// (created manually)
		IObservableValue<String> observeTextTextPairModelRatioRsiThresholdObserveWidget = WidgetProperties.text(SWT.Modify).observe(textPairModelRatioRsiThreshold);
		IObservableValue tableViewerPortfPairsRatioRsiThresholdObserveDetailValue = BeanProperties.value(PairStrategy.class, "ratioRsiThreshold", Double.class).observeDetail(observeSingleSelectionTableViewerPortfPairs_9);
		bindingContext.bindValue(observeTextTextPairModelRatioRsiThresholdObserveWidget, tableViewerPortfPairsRatioRsiThresholdObserveDetailValue, null, null);
		//
		IObservableValue<String> observeTextTextPairModelResidualLinRegPeriodObserveWidget = WidgetProperties.text(SWT.Modify).observe(textPairModelResidualLinRegPeriod);
		IObservableValue observeSingleSelectionTableViewerPortfPairs_10 = ViewerProperties.singleSelection().observe(tableViewerPortfPairs);
		IObservableValue tableViewerPortfPairsResidualLinRegPeriodObserveDetailValue = BeanProperties.value(PairStrategy.class, "residualLinRegPeriod", Integer.class).observeDetail(observeSingleSelectionTableViewerPortfPairs_10);
		bindingContext.bindValue(observeTextTextPairModelResidualLinRegPeriodObserveWidget, tableViewerPortfPairsResidualLinRegPeriodObserveDetailValue, null, null);
		//
		IObservableValue<Integer> observeSingleSelectionIndexComboPairModelRatioEntryModeObserveWidget = WidgetProperties.singleSelectionIndex().observe(comboPairModelRatioEntryMode);
		IObservableValue observeSingleSelectionTableViewerPortfPairs_11 = ViewerProperties.singleSelection().observe(tableViewerPortfPairs);
		IObservableValue tableViewerPortfPairsRatioEntryModeObserveDetailValue = BeanProperties.value(PairStrategy.class, "entryMode", Integer.class).observeDetail(observeSingleSelectionTableViewerPortfPairs_11);
		bindingContext.bindValue(observeSingleSelectionIndexComboPairModelRatioEntryModeObserveWidget, tableViewerPortfPairsRatioEntryModeObserveDetailValue, null, null);
        //
        IObservableValue<Integer> observeSingleSelectionIndexComboPairModelNeutralityObserveWidget = WidgetProperties.singleSelectionIndex().observe(comboPairModelNeutrality);
        IObservableValue observeSingleSelectionTableViewerPortfPairsNeutrality = ViewerProperties.singleSelection().observe(tableViewerPortfPairs);
        IObservableValue tableViewerPortfPairsNeutralityObserveDetailValue = BeanProperties.value(PairStrategy.class, "neutrality", Integer.class).observeDetail(observeSingleSelectionTableViewerPortfPairsNeutrality);
        bindingContext.bindValue(observeSingleSelectionIndexComboPairModelNeutralityObserveWidget, tableViewerPortfPairsNeutralityObserveDetailValue, null, null);
		//
		IObservableValue<String> observeTextTextPairModelKalmanAutoVeObserveWidget = WidgetProperties.text(SWT.Modify).observe(textPairModelKalmanAutoVe);
		IObservableValue observeSingleSelectionTableViewerPortfPairs_KalmanAutoVe = ViewerProperties.singleSelection().observe(tableViewerPortfPairs);
		IObservableValue tableViewerPortfPairsKalmanAutoVeObserveDetailValue = BeanProperties.value(PairStrategy.class, "kalmanAutoVe", Double.class).observeDetail(observeSingleSelectionTableViewerPortfPairs_KalmanAutoVe);
		bindingContext.bindValue(observeTextTextPairModelKalmanAutoVeObserveWidget, tableViewerPortfPairsKalmanAutoVeObserveDetailValue, null, null);
		//
		IObservableValue<String> observeTextTextPairModelKalmanAutoUsageTargetObserveWidget = WidgetProperties.text(SWT.Modify).observe(textPairModelKalmanAutoUsageTarget);
		IObservableValue observeSingleSelectionTableViewerPortfPairs_KalmanAutoUsageTarget = ViewerProperties.singleSelection().observe(tableViewerPortfPairs);
		IObservableValue tableViewerPortfPairsKalmanAutoUsageTargetObserveDetailValue = BeanProperties.value(PairStrategy.class, "kalmanAutoUsageTarget", Double.class).observeDetail(observeSingleSelectionTableViewerPortfPairs_KalmanAutoUsageTarget);
		bindingContext.bindValue(observeTextTextPairModelKalmanAutoUsageTargetObserveWidget, tableViewerPortfPairsKalmanAutoUsageTargetObserveDetailValue, null, null);
		//
		IObservableValue<Integer> observeSelectionSpinPairXtraMaxDaysObserveWidget = WidgetProperties.spinnerSelection().observeDelayed(1000, spinPairXtraMaxDays);
		IObservableValue observeSingleSelectionTableViewerPortfPairs_12 = ViewerProperties.singleSelection().observe(tableViewerPortfPairs);
		IObservableValue tableViewerPortfPairsMaxDaysObserveDetailValue = BeanProperties.value(PairStrategy.class, "maxDays", Integer.class).observeDetail(observeSingleSelectionTableViewerPortfPairs_12);
		bindingContext.bindValue(observeSelectionSpinPairXtraMaxDaysObserveWidget, tableViewerPortfPairsMaxDaysObserveDetailValue, null, null);
		//
		IObservableValue<Boolean> observeSelectionBtnCheckPairXtraMaxDaysObserveWidget = WidgetProperties.buttonSelection().observe(btnCheckPairXtraMaxDays);
		IObservableValue observeSingleSelectionTableViewerPortfPairs_13 = ViewerProperties.singleSelection().observe(tableViewerPortfPairs);
		IObservableValue tableViewerPortfPairsMaxDaysEnabledObserveDetailValue = BeanProperties.value(PairStrategy.class, "maxDaysEnabled", Boolean.class).observeDetail(observeSingleSelectionTableViewerPortfPairs_13);
		bindingContext.bindValue(observeSelectionBtnCheckPairXtraMaxDaysObserveWidget, tableViewerPortfPairsMaxDaysEnabledObserveDetailValue, null, null);
		//
		IObservableValue<String> observeTextTextPairXtraMinPlObserveWidget = WidgetProperties.text(SWT.Modify).observeDelayed(1000, textPairXtraMinPl);
		IObservableValue observeSingleSelectionTableViewerPortfPairs_14 = ViewerProperties.singleSelection().observe(tableViewerPortfPairs);
		IObservableValue tableViewerPortfPairsMinPlToCloseObserveDetailValue = BeanProperties.value(PairStrategy.class, "minPlToClose", Double.class).observeDetail(observeSingleSelectionTableViewerPortfPairs_14);
		textPairXtraMinPlBinding = bindingContext.bindValue(observeTextTextPairXtraMinPlObserveWidget, tableViewerPortfPairsMinPlToCloseObserveDetailValue, null, null);
		//
		IObservableValue<Boolean> observeSelectionBtnCheckPairXtraMinPlObserveWidget = WidgetProperties.buttonSelection().observe(btnCheckPairXtraMinPl);
		IObservableValue observeSingleSelectionTableViewerPortfPairs_15 = ViewerProperties.singleSelection().observe(tableViewerPortfPairs);
		IObservableValue tableViewerPortfPairsMinPlToCloseEnabledObserveDetailValue = BeanProperties.value(PairStrategy.class, "minPlToCloseEnabled", Boolean.class).observeDetail(observeSingleSelectionTableViewerPortfPairs_15);
		bindingContext.bindValue(observeSelectionBtnCheckPairXtraMinPlObserveWidget, tableViewerPortfPairsMinPlToCloseEnabledObserveDetailValue, null, null);
		//
		IObservableValue<String> observeTextTextPairXtraMinPriceObserveWidget = WidgetProperties.text(SWT.Modify).observeDelayed(1000, textPairXtraMinPrice);
		IObservableValue observeSingleSelectionTableViewerPortfPairs_16 = ViewerProperties.singleSelection().observe(tableViewerPortfPairs);
		IObservableValue tableViewerPortfPairsMinPriceObserveDetailValue = BeanProperties.value(PairStrategy.class, "minPrice", Double.class).observeDetail(observeSingleSelectionTableViewerPortfPairs_16);
		textPairXtraMinPriceBinding = bindingContext.bindValue(observeTextTextPairXtraMinPriceObserveWidget, tableViewerPortfPairsMinPriceObserveDetailValue, null, null);
		//
		IObservableValue<Boolean> observeSelectionBtnCheckPairXtraMinPriceObserveWidget = WidgetProperties.buttonSelection().observe(btnCheckPairXtraMinPrice);
		IObservableValue observeSingleSelectionTableViewerPortfPairs_17 = ViewerProperties.singleSelection().observe(tableViewerPortfPairs);
		IObservableValue tableViewerPortfPairsMinPriceEnabledObserveDetailValue = BeanProperties.value(PairStrategy.class, "minPriceEnabled", Boolean.class).observeDetail(observeSingleSelectionTableViewerPortfPairs_17);
		bindingContext.bindValue(observeSelectionBtnCheckPairXtraMinPriceObserveWidget, tableViewerPortfPairsMinPriceEnabledObserveDetailValue, null, null);
		//
		IObservableValue<String> observeTextTextPairXtraMinExpectObserveWidget = WidgetProperties.text(SWT.Modify).observeDelayed(1000, textPairXtraMinExpect);
		IObservableValue observeSingleSelectionTableViewerPortfPairs_18 = ViewerProperties.singleSelection().observe(tableViewerPortfPairs);
		IObservableValue tableViewerPortfPairsMinExpectationObserveDetailValue = BeanProperties.value(PairStrategy.class, "minExpectation", Double.class).observeDetail(observeSingleSelectionTableViewerPortfPairs_18);
		textPairXtraMinExpectBinding = bindingContext.bindValue(observeTextTextPairXtraMinExpectObserveWidget, tableViewerPortfPairsMinExpectationObserveDetailValue, null, null);
		//
		IObservableValue<Boolean> observeSelectionBtnCheckPairXtraMinExpectObserveWidget = WidgetProperties.buttonSelection().observe(btnCheckPairXtraMinExpect);
		IObservableValue observeSingleSelectionTableViewerPortfPairs_19 = ViewerProperties.singleSelection().observe(tableViewerPortfPairs);
		IObservableValue tableViewerPortfPairsMinExpectationEnabledObserveDetailValue = BeanProperties.value(PairStrategy.class, "minExpectationEnabled", Boolean.class).observeDetail(observeSingleSelectionTableViewerPortfPairs_19);
		bindingContext.bindValue(observeSelectionBtnCheckPairXtraMinExpectObserveWidget, tableViewerPortfPairsMinExpectationEnabledObserveDetailValue, null, null);
		//
		IObservableValue<Integer> observeSelectionSpinPairEntryTimeStartHourObserveWidget = WidgetProperties.spinnerSelection().observeDelayed(1000, spinPairEntryTimeStartHour);
		IObservableValue observeSingleSelectionTableViewerPortfPairs_20 = ViewerProperties.singleSelection().observe(tableViewerPortfPairs);
		IObservableValue tableViewerPortfPairsEntryStartHourObserveDetailValue = BeanProperties.value(PairStrategy.class, "entryStartHour", Integer.class).observeDetail(observeSingleSelectionTableViewerPortfPairs_20);
		bindingContext.bindValue(observeSelectionSpinPairEntryTimeStartHourObserveWidget, tableViewerPortfPairsEntryStartHourObserveDetailValue, null, null);
		//
		IObservableValue<Integer> observeSelectionSpinPairEntryTimeStartMinuteObserveWidget = WidgetProperties.spinnerSelection().observeDelayed(1000, spinPairEntryTimeStartMinute);
		IObservableValue observeSingleSelectionTableViewerPortfPairs_21 = ViewerProperties.singleSelection().observe(tableViewerPortfPairs);
		IObservableValue tableViewerPortfPairsEntryStartMinuteObserveDetailValue = BeanProperties.value(PairStrategy.class, "entryStartMinute", Integer.class).observeDetail(observeSingleSelectionTableViewerPortfPairs_21);
		bindingContext.bindValue(observeSelectionSpinPairEntryTimeStartMinuteObserveWidget, tableViewerPortfPairsEntryStartMinuteObserveDetailValue, null, null);
		//
		IObservableValue<Integer> observeSelectionSpinPairEntryTimeEndHourObserveWidget = WidgetProperties.spinnerSelection().observeDelayed(1000, spinPairEntryTimeEndHour);
		IObservableValue observeSingleSelectionTableViewerPortfPairs_22 = ViewerProperties.singleSelection().observe(tableViewerPortfPairs);
		IObservableValue tableViewerPortfPairsEntryEndHourObserveDetailValue = BeanProperties.value(PairStrategy.class, "entryEndHour", Integer.class).observeDetail(observeSingleSelectionTableViewerPortfPairs_22);
		bindingContext.bindValue(observeSelectionSpinPairEntryTimeEndHourObserveWidget, tableViewerPortfPairsEntryEndHourObserveDetailValue, null, null);
		//
		IObservableValue<Integer> observeSelectionSpinPairEntryTimeEndMinuteObserveWidget = WidgetProperties.spinnerSelection().observeDelayed(1000, spinPairEntryTimeEndMinute);
		IObservableValue observeSingleSelectionTableViewerPortfPairs_23 = ViewerProperties.singleSelection().observe(tableViewerPortfPairs);
		IObservableValue tableViewerPortfPairsEntryEndMinuteObserveDetailValue = BeanProperties.value(PairStrategy.class, "entryEndMinute", Integer.class).observeDetail(observeSingleSelectionTableViewerPortfPairs_23);
		bindingContext.bindValue(observeSelectionSpinPairEntryTimeEndMinuteObserveWidget, tableViewerPortfPairsEntryEndMinuteObserveDetailValue, null, null);
		//
		IObservableValue<Integer> observeSelectionSpinPairExitTimeStartHourObserveWidget = WidgetProperties.spinnerSelection().observeDelayed(1000, spinPairExitTimeStartHour);
		IObservableValue observeSingleSelectionTableViewerPortfPairs_24 = ViewerProperties.singleSelection().observe(tableViewerPortfPairs);
		IObservableValue tableViewerPortfPairsExitStartHourObserveDetailValue = BeanProperties.value(PairStrategy.class, "exitStartHour", Integer.class).observeDetail(observeSingleSelectionTableViewerPortfPairs_24);
		bindingContext.bindValue(observeSelectionSpinPairExitTimeStartHourObserveWidget, tableViewerPortfPairsExitStartHourObserveDetailValue, null, null);
		//
		IObservableValue<Integer> observeSelectionSpinExitTimeStartMinuteObserveWidget = WidgetProperties.spinnerSelection().observeDelayed(1000, spinExitTimeStartMinute);
		IObservableValue observeSingleSelectionTableViewerPortfPairs_25 = ViewerProperties.singleSelection().observe(tableViewerPortfPairs);
		IObservableValue tableViewerPortfPairsExitStartMinuteObserveDetailValue = BeanProperties.value(PairStrategy.class, "exitStartMinute", Integer.class).observeDetail(observeSingleSelectionTableViewerPortfPairs_25);
		bindingContext.bindValue(observeSelectionSpinExitTimeStartMinuteObserveWidget, tableViewerPortfPairsExitStartMinuteObserveDetailValue, null, null);
		//
		IObservableValue<Integer> observeSelectionSpinPairExitTimeEndHourObserveWidget = WidgetProperties.spinnerSelection().observeDelayed(1000, spinPairExitTimeEndHour);
		IObservableValue observeSingleSelectionTableViewerPortfPairs_26 = ViewerProperties.singleSelection().observe(tableViewerPortfPairs);
		IObservableValue tableViewerPortfPairsExitEndHourObserveDetailValue = BeanProperties.value(PairStrategy.class, "exitEndHour", Integer.class).observeDetail(observeSingleSelectionTableViewerPortfPairs_26);
		bindingContext.bindValue(observeSelectionSpinPairExitTimeEndHourObserveWidget, tableViewerPortfPairsExitEndHourObserveDetailValue, null, null);
		//
		IObservableValue<Integer> observeSelectionSpinPairExitTimeEndMinuteObserveWidget = WidgetProperties.spinnerSelection().observeDelayed(1000, spinPairExitTimeEndMinute);
		IObservableValue observeSingleSelectionTableViewerPortfPairs_27 = ViewerProperties.singleSelection().observe(tableViewerPortfPairs);
		IObservableValue tableViewerPortfPairsExitEndMinuteObserveDetailValue = BeanProperties.value(PairStrategy.class, "exitEndMinute", Integer.class).observeDetail(observeSingleSelectionTableViewerPortfPairs_27);
		bindingContext.bindValue(observeSelectionSpinPairExitTimeEndMinuteObserveWidget, tableViewerPortfPairsExitEndMinuteObserveDetailValue, null, null);
		//
		IObservableValue<String> observeTextTextPairTimezoneObserveWidget = WidgetProperties.text(SWT.Modify).observe(textPairTimezone);
		IObservableValue observeSingleSelectionTableViewerPortfPairs_28 = ViewerProperties.singleSelection().observe(tableViewerPortfPairs);
		IObservableValue tableViewerPortfPairsTimezoneIdObserveDetailValue = BeanProperties.value(PairStrategy.class, "timezoneId", String.class).observeDetail(observeSingleSelectionTableViewerPortfPairs_28);
		bindingContext.bindValue(observeTextTextPairTimezoneObserveWidget, tableViewerPortfPairsTimezoneIdObserveDetailValue, null, null);
		//
		IObservableValue<String> observeTextTextPairMargin1ObserveWidget = WidgetProperties.text(SWT.Modify).observeDelayed(500, textPairMargin1);
		IObservableValue observeSingleSelectionTableViewerPortfPairs_29 = ViewerProperties.singleSelection().observe(tableViewerPortfPairs);
		IObservableValue tableViewerPortfPairsMarginPerc1ObserveDetailValue = BeanProperties.value(PairStrategy.class, "marginPerc1", Double.class).observeDetail(observeSingleSelectionTableViewerPortfPairs_29);
		UpdateValueStrategy<String, Object> strategy_8 = new UpdateValueStrategy<>();
		strategy_8.setBeforeSetValidator(new MarginPercents());
		textPairMargin1Binding = bindingContext.bindValue(observeTextTextPairMargin1ObserveWidget, tableViewerPortfPairsMarginPerc1ObserveDetailValue, strategy_8, null);
		//
		IObservableValue<String> observeTextTextPairMargin2ObserveWidget = WidgetProperties.text(SWT.Modify).observeDelayed(500, textPairMargin2);
		IObservableValue observeSingleSelectionTableViewerPortfPairs_30 = ViewerProperties.singleSelection().observe(tableViewerPortfPairs);
		IObservableValue tableViewerPortfPairsMarginPerc2ObserveDetailValue = BeanProperties.value(PairStrategy.class, "marginPerc2", Double.class).observeDetail(observeSingleSelectionTableViewerPortfPairs_30);
		UpdateValueStrategy<String, Object> strategy_9 = new UpdateValueStrategy<>();
		strategy_9.setBeforeSetValidator(new MarginPercents());
		textPairMargin2Binding = bindingContext.bindValue(observeTextTextPairMargin2ObserveWidget, tableViewerPortfPairsMarginPerc2ObserveDetailValue, strategy_9, null);

		IObservableValue<Boolean> observeSelectionBtnCheckPairAllowReversalsObserveWidget = WidgetProperties.buttonSelection().observe(btnCheckPairAllowReversals);
		IObservableValue observeSingleSelectionTableViewerPortfPairsAllowReversals = ViewerProperties.singleSelection().observe(tableViewerPortfPairs);
		IObservableValue tableViewerPortfPairsAllowReversalsObserveDetailValue = BeanProperties.value(PairStrategy.class, "allowReversals", Boolean.class).observeDetail(observeSingleSelectionTableViewerPortfPairs_17);
		bindingContext.bindValue(observeSelectionBtnCheckPairAllowReversalsObserveWidget, tableViewerPortfPairsAllowReversalsObserveDetailValue, null, null);

		//
		IObservableValue<Integer> observeSingleSelectionIndexComboPairStatusObserveWidget = WidgetProperties.singleSelectionIndex().observe(comboPairStatus);
		IObservableValue observeSingleSelectionTableViewerPortfPairs_31 = ViewerProperties.singleSelection().observe(tableViewerPortfPairs);
		IObservableValue tableViewerPortfPairsTradingStatusObserveDetailValue = BeanProperties.value(PairStrategy.class, "tradingStatus", Integer.class).observeDetail(observeSingleSelectionTableViewerPortfPairs_31);
		bindingContext.bindValue(observeSingleSelectionIndexComboPairStatusObserveWidget, tableViewerPortfPairsTradingStatusObserveDetailValue, null, null);
		//
		IObservableValue<Integer> observeSingleSelectionIndexComboPairRestrictionObserveWidget = WidgetProperties.singleSelectionIndex().observe(comboPairRestriction);
		IObservableValue observeSingleSelectionTableViewerPortfPairs_32 = ViewerProperties.singleSelection().observe(tableViewerPortfPairs);
		IObservableValue tableViewerPortfPairsAllowPositionsObserveDetailValue = BeanProperties.value(PairStrategy.class, "allowPositions", Integer.class).observeDetail(observeSingleSelectionTableViewerPortfPairs_32);
		bindingContext.bindValue(observeSingleSelectionIndexComboPairRestrictionObserveWidget, tableViewerPortfPairsAllowPositionsObserveDetailValue, null, null);
		//
		IObservableValue<String> observeTextTextPortfNameObserveWidget = WidgetProperties.text(SWT.Modify).observeDelayed(500, textPortfName);
		IObservableValue observeSingleSelectionTableViewerPortfolios_1 = ViewerProperties.singleSelection().observe(tableViewerPortfolios);
		IObservableValue tableViewerPortfoliosNameObserveDetailValue = BeanProperties.value(Portfolio.class, "name", String.class).observeDetail(observeSingleSelectionTableViewerPortfolios_1);
		UpdateValueStrategy<String, Object> strategy_7 = new UpdateValueStrategy<>();
		strategy_7.setBeforeSetValidator(new StringNotEmpty());
		textPortfNameBinding = bindingContext.bindValue(observeTextTextPortfNameObserveWidget, tableViewerPortfoliosNameObserveDetailValue, strategy_7, null);
		//
		IObservableValue<String> observeTextTextPortfPairsObserveWidget = WidgetProperties.text(SWT.Modify).observe(textPortfPairs);
		IObservableValue observeSingleSelectionTableViewerPortfolios_2 = ViewerProperties.singleSelection().observe(tableViewerPortfolios);
		IObservableValue tableViewerPortfoliosPairCountObserveDetailValue = BeanProperties.value(Portfolio.class, "pairCount", Integer.class).observeDetail(observeSingleSelectionTableViewerPortfolios_2);
		bindingContext.bindValue(observeTextTextPortfPairsObserveWidget, tableViewerPortfoliosPairCountObserveDetailValue, null, null);
		//
		IObservableValue<Integer> observeSelectionSpinnerPortfMaxPairsOpenObserveWidget = WidgetProperties.spinnerSelection().observeDelayed(500, spinnerPortfMaxPairsOpen);
		IObservableValue observeSingleSelectionTableViewerPortfolios_3 = ViewerProperties.singleSelection().observe(tableViewerPortfolios);
		IObservableValue tableViewerPortfoliosMaxPairsObserveDetailValue = BeanProperties.value(Portfolio.class, "maxPairs", Integer.class).observeDetail(observeSingleSelectionTableViewerPortfolios_3);
		bindingContext.bindValue(observeSelectionSpinnerPortfMaxPairsOpenObserveWidget, tableViewerPortfoliosMaxPairsObserveDetailValue, null, null);
		//
		IObservableValue<Integer> observeSelectionSpinnerPortfAllocPercObserveWidget = WidgetProperties.spinnerSelection().observeDelayed(500, spinnerPortfAllocPerc);
		IObservableValue observeSingleSelectionTableViewerPortfolios_4 = ViewerProperties.singleSelection().observe(tableViewerPortfolios);
		IObservableValue tableViewerPortfoliosAccountAllocObserveDetailValue = BeanProperties.value(Portfolio.class, "accountAlloc", Integer.class).observeDetail(observeSingleSelectionTableViewerPortfolios_4);
		bindingContext.bindValue(observeSelectionSpinnerPortfAllocPercObserveWidget, tableViewerPortfoliosAccountAllocObserveDetailValue, null, null);
		//
		IObservableValue<String> observeTextTextPortfBoundToObserveWidget = WidgetProperties.text(SWT.Modify).observe(textPortfBoundTo);
		IObservableValue observeSingleSelectionTableViewerPortfolios_5 = ViewerProperties.singleSelection().observe(tableViewerPortfolios);
		IObservableValue tableViewerPortfoliosAccountCodeObserveDetailValue = BeanProperties.value(Portfolio.class, "accountCode", String.class).observeDetail(observeSingleSelectionTableViewerPortfolios_5);
		bindingContext.bindValue(observeTextTextPortfBoundToObserveWidget, tableViewerPortfoliosAccountCodeObserveDetailValue, null, null);
		//
		IObservableValue<Integer> observeSingleSelectionIndexComboPortfMasterStatusObserveWidget = WidgetProperties.singleSelectionIndex().observe(comboPortfMasterStatus);
		IObservableValue observeSingleSelectionTableViewerPortfolios_6 = ViewerProperties.singleSelection().observe(tableViewerPortfolios);
		IObservableValue tableViewerPortfoliosMasterStatusObserveDetailValue = BeanProperties.value(Portfolio.class, "masterStatus", Integer.class).observeDetail(observeSingleSelectionTableViewerPortfolios_6);
		bindingContext.bindValue(observeSingleSelectionIndexComboPortfMasterStatusObserveWidget, tableViewerPortfoliosMasterStatusObserveDetailValue, null, null);
		//
		IObservableValue<Integer> observeSingleSelectionIndexComboPortfDayTradingObserveWidget = WidgetProperties.singleSelectionIndex().observe(comboPortfDayTrading);
		IObservableValue observeSingleSelectionTableViewerPortfolios_7 = ViewerProperties.singleSelection().observe(tableViewerPortfolios);
		IObservableValue tableViewerPortfoliosPdtEnableObserveDetailValue = BeanProperties.value(Portfolio.class, "pdtEnable", Integer.class).observeDetail(observeSingleSelectionTableViewerPortfolios_7);
		bindingContext.bindValue(observeSingleSelectionIndexComboPortfDayTradingObserveWidget, tableViewerPortfoliosPdtEnableObserveDetailValue, null, null);
		//
		ObservableListContentProvider listContentProvider_6 = new ObservableListContentProvider();
		IObservableMap observeMap_1 = BeansObservables.observeMap(listContentProvider_6.getKnownElements(), Account.class, "code");
		comboViewerPortfAccounts.setLabelProvider(new ObservableMapLabelProvider(observeMap_1));
		comboViewerPortfAccounts.setContentProvider(listContentProvider_6);
		//
		IObservableList accountsMAccountListObserveList = BeanProperties.list("accounts").observe(mAccountList);
		comboViewerPortfAccounts.setInput(accountsMAccountListObserveList);
		//
		IObservableValue<Boolean> observeEnabledBtnPortfBindObserveWidget = WidgetProperties.enabled().observe(btnPortfBind);
		IObservableValue observeSingleSelectionTableViewerPortfolios_8 = ViewerProperties.singleSelection().observe(tableViewerPortfolios);
		IObservableValue tableViewerPortfoliosBindEnabledObserveDetailValue = BeanProperties.value(Portfolio.class, "bindEnabled", Boolean.class).observeDetail(observeSingleSelectionTableViewerPortfolios_8);
		bindingContext.bindValue(observeEnabledBtnPortfBindObserveWidget, tableViewerPortfoliosBindEnabledObserveDetailValue, null, null);
		//
		IObservableValue<Boolean> observeEnabledBtnPortfUnbindObserveWidget = WidgetProperties.enabled().observe(btnPortfUnbind);
		IObservableValue observeSingleSelectionTableViewerPortfolios_9 = ViewerProperties.singleSelection().observe(tableViewerPortfolios);
		IObservableValue tableViewerPortfoliosUnbindEnabledObserveDetailValue = BeanProperties.value(Portfolio.class, "unbindEnabled", Boolean.class).observeDetail(observeSingleSelectionTableViewerPortfolios_9);
		bindingContext.bindValue(observeEnabledBtnPortfUnbindObserveWidget, tableViewerPortfoliosUnbindEnabledObserveDetailValue, null, null);
		//
		IObservableValue<Boolean> observeEnabledBtnPairCloseObserveWidget = WidgetProperties.enabled().observe(btnPairClose);
		IObservableValue observeSingleSelectionTableViewerPortfPairs_33 = ViewerProperties.singleSelection().observe(tableViewerPortfPairs);
		IObservableValue tableViewerPortfPairsCloseableObserveDetailValue = BeanProperties.value(PairStrategy.class, "closeable", Boolean.class).observeDetail(observeSingleSelectionTableViewerPortfPairs_33);
		bindingContext.bindValue(observeEnabledBtnPairCloseObserveWidget, tableViewerPortfPairsCloseableObserveDetailValue, null, null);
		//
		IObservableValue<Boolean> observeEnabledBtnPairOpenLongObserveWidget = WidgetProperties.enabled().observe(btnPairOpenLong);
		IObservableValue observeSingleSelectionTableViewerPortfPairs_34 = ViewerProperties.singleSelection().observe(tableViewerPortfPairs);
		IObservableValue tableViewerPortfPairsOpenableObserveDetailValue = BeanProperties.value(PairStrategy.class, "openable", Boolean.class).observeDetail(observeSingleSelectionTableViewerPortfPairs_34);
		bindingContext.bindValue(observeEnabledBtnPairOpenLongObserveWidget, tableViewerPortfPairsOpenableObserveDetailValue, null, null);
		//
		IObservableValue<Boolean> observeEnabledBtnPairOpenShortObserveWidget = WidgetProperties.enabled().observe(btnPairOpenShort);
		IObservableValue observeSingleSelectionTableViewerPortfPairs_35 = ViewerProperties.singleSelection().observe(tableViewerPortfPairs);
		IObservableValue tableViewerPortfPairsOpenableObserveDetailValue_1 = BeanProperties.value(PairStrategy.class, "openable", Boolean.class).observeDetail(observeSingleSelectionTableViewerPortfPairs_35);
		bindingContext.bindValue(observeEnabledBtnPairOpenShortObserveWidget, tableViewerPortfPairsOpenableObserveDetailValue_1, null, null);
		//
		IObservableValue<Boolean> observeEnabledBtnPairDeleteObserveWidget = WidgetProperties.enabled().observe(btnPairDelete);
		IObservableValue observeSingleSelectionTableViewerPortfPairs_36 = ViewerProperties.singleSelection().observe(tableViewerPortfPairs);
		IObservableValue tableViewerPortfPairsOpenableObserveDetailValue_2 = BeanProperties.value(PairStrategy.class, "deletable", Boolean.class).observeDetail(observeSingleSelectionTableViewerPortfPairs_36);
		bindingContext.bindValue(observeEnabledBtnPairDeleteObserveWidget, tableViewerPortfPairsOpenableObserveDetailValue_2, null, null);
		//
		IObservableValue<String> observeTextTextPTLAccessKeyObserveWidget = WidgetProperties.text(SWT.Modify).observeDelayed(300, textPTLAccessKey);
		IObservableValue ptlAccessKeyMSettingsObserveValue = BeanProperties.value("ptlAccessKey").observe(mSettings);
		bindingContext.bindValue(observeTextTextPTLAccessKeyObserveWidget, ptlAccessKeyMSettingsObserveValue, null, null);
		//
		IObservableValue<String> observeTextTextPTLAccessTokenObserveWidget = WidgetProperties.text(SWT.Modify).observeDelayed(300, textPTLAccessToken);
		IObservableValue ptlSecretKeyMSettingsObserveValue = BeanProperties.value("ptlSecretKey").observe(mSettings);
		bindingContext.bindValue(observeTextTextPTLAccessTokenObserveWidget, ptlSecretKeyMSettingsObserveValue, null, null);
		//
		IObservableValue<Boolean> observeEnabledBtnPTLConnectObserveWidget = WidgetProperties.enabled().observe(btnPTLConnect);
		IObservableValue ptlConnectEnabledMSettingsObserveValue = BeanProperties.value("ptlConnectEnabled").observe(mSettings);
		bindingContext.bindValue(observeEnabledBtnPTLConnectObserveWidget, ptlConnectEnabledMSettingsObserveValue, null, null);
		//
		ObservableListContentProvider listContentProvider_7 = new ObservableListContentProvider();
		IObservableMap[] observeMaps_5 = BeansObservables.observeMaps(listContentProvider_7.getKnownElements(), LegHistoryEntry.class, new String[]{"datetimeS", "account", "symbol", "action", "realizedPlS", "qty", "priceS", "valueS", "commissionsS", "fillTimeS", "slippageS"});
		tableViewerLegHistory.setLabelProvider(new ObservableMapLabelProvider(observeMaps_5));
		tableViewerLegHistory.setContentProvider(listContentProvider_7);
		//
		IObservableList entriesMLegHistoryObserveList = BeanProperties.list("entries").observe(mLegHistory);
		tableViewerLegHistory.setInput(entriesMLegHistoryObserveList);
		//
		ObservableListContentProvider listContentProvider_8 = new ObservableListContentProvider();
		IObservableMap[] observeMaps_6 = BeansObservables.observeMaps(listContentProvider_8.getKnownElements(), Account.class, new String[]{"code", "currency", "equityWithLoanValueS", "buyingPowerS", "totalCashS", "initialMarginS", "maintenanceMarginS", "unrealizedPlS", "availableFundsS"});
		tableViewerAccounts.setLabelProvider(new ObservableMapLabelProvider(observeMaps_6));
		tableViewerAccounts.setContentProvider(listContentProvider_8);
		//
		tableViewerAccounts.setInput(accountsMAccountListObserveList);
		//
		IObservableValue<String> observeTextTextPairBasicSlotOccupationObserveWidget = WidgetProperties.text(SWT.Modify).observeDelayed(500, textPairBasicSlotOccupation);
		IObservableValue observeSingleSelectionTableViewerPortfPairs_37 = ViewerProperties.singleSelection().observe(tableViewerPortfPairs);
		IObservableValue tableViewerPortfPairsSlotOccupationObserveDetailValue = BeanProperties.value(PairStrategy.class, "slotOccupation", Double.class).observeDetail(observeSingleSelectionTableViewerPortfPairs_37);
		UpdateValueStrategy<String, Object> strategy_10 = new UpdateValueStrategy<String, Object>();
		strategy_10.setBeforeSetValidator(new SlotOccupation());
		textPairBasicSlotOccupationBinding = bindingContext.bindValue(observeTextTextPairBasicSlotOccupationObserveWidget, tableViewerPortfPairsSlotOccupationObserveDetailValue, strategy_10, null);
		//
		IObservableValue<Boolean> observeEnabledSpinnerPortfMaxPairsOpenObserveWidget = WidgetProperties.enabled().observe(spinnerPortfMaxPairsOpen);
		IObservableValue observeSingleSelectionTableViewerPortfolios_10 = ViewerProperties.singleSelection().observe(tableViewerPortfolios);
		IObservableValue tableViewerPortfoliosBindEnabledObserveDetailValue_1 = BeanProperties.value(Portfolio.class, "bindEnabled", Boolean.class).observeDetail(observeSingleSelectionTableViewerPortfolios_10);
		bindingContext.bindValue(observeEnabledSpinnerPortfMaxPairsOpenObserveWidget, tableViewerPortfoliosBindEnabledObserveDetailValue_1, null, null);
		//
		IObservableValue<Boolean> observeSelectionBtnPTLSaveTokenObserveWidget = WidgetProperties.buttonSelection().observeDelayed(300, btnPTLSaveToken);
		IObservableValue savePtlSecretKeyMSettingsObserveValue = BeanProperties.value("savePtlSecretKey").observe(mSettings);
		bindingContext.bindValue(observeSelectionBtnPTLSaveTokenObserveWidget, savePtlSecretKeyMSettingsObserveValue, null, null);
		//
		IObservableValue<Boolean> observeSelectionBtnConfidentialModeObserveWidget = WidgetProperties.buttonSelection().observeDelayed(300, btnConfidentialMode);
		IObservableValue enableConfidentialModeMSettingsObserveValue = BeanProperties.value("enableConfidentialMode").observe(mSettings);
		bindingContext.bindValue(observeSelectionBtnConfidentialModeObserveWidget, enableConfidentialModeMSettingsObserveValue, null, null);
		//
		IObservableValue<Boolean> observeEnabledBtnPairResumeObserveWidget = WidgetProperties.enabled().observe(btnPairResume);
		IObservableValue observeSingleSelectionTableViewerPortfPairs_38 = ViewerProperties.singleSelection().observe(tableViewerPortfPairs);
		IObservableValue tableViewerPortfPairsResumableObserveDetailValue = BeanProperties.value(PairStrategy.class, "resumable", Boolean.class).observeDetail(observeSingleSelectionTableViewerPortfPairs_38);
		bindingContext.bindValue(observeEnabledBtnPairResumeObserveWidget, tableViewerPortfPairsResumableObserveDetailValue, null, null);
		//
		IObservableValue<Boolean> observeEnabledTextPTLAccessKeyObserveWidget = WidgetProperties.enabled().observe(textPTLAccessKey);
		UpdateValueStrategy strategy_11 = new UpdateValueStrategy();
		strategy_11.setConverter(new Inverter());
		bindingContext.bindValue(observeEnabledTextPTLAccessKeyObserveWidget, ptlConnectedMStatusObserveValue, null, strategy_11);
		//
		IObservableValue<Boolean> observeEnabledTextPTLAccessTokenObserveWidget = WidgetProperties.enabled().observe(textPTLAccessToken);
		UpdateValueStrategy strategy_12 = new UpdateValueStrategy();
		strategy_12.setConverter(new Inverter());
		bindingContext.bindValue(observeEnabledTextPTLAccessTokenObserveWidget, ptlConnectedMStatusObserveValue, null, strategy_12);
		//
		return bindingContext;
	}
}