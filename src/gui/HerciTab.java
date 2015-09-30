package gui;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.border.EmptyBorder;
import javax.swing.plaf.SplitPaneUI;
import javax.swing.plaf.basic.BasicSplitPaneUI;
import javax.swing.table.TableColumn;

import telemetry.PayloadHERCIhighSpeed;
import telemetry.PayloadRadExpData;
import common.Config;
import common.Log;
import common.Spacecraft;
import decoder.Decoder;

/**
 * 
 * FOX 1 Telemetry Decoder
 * @author chris.e.thompson g0kla/ac2cz
 *
 * Copyright (C) 2015 amsat.org
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */
@SuppressWarnings("serial")
public class HerciTab extends RadiationTab implements Runnable, ItemListener {

	public static final String HERCITAB = "HERCITAB";
	public final int DEFAULT_DIVIDER_LOCATION = 410;
	PayloadHERCIhighSpeed hsPayload;

	JLabel lblFramesDecoded;
	JLabel lblHSpayload;
	int displayRows = PayloadHERCIhighSpeed.MAX_PAYLOAD_SIZE/32+1;
	JLabel[] lblBytes = new JLabel[displayRows];
	
	RadiationTableModel radTableModel;
	JTable table;
	JScrollPane scrollPane;
	
	private static final String DECODED = "HS Payloads Decoded: ";

	
	public HerciTab(Spacecraft sat) {
		super();
		fox = sat;
		foxId = fox.foxId;

		JLabel lblId = new JLabel("University of Iowa High Energy Radiation CubeSat Instrument (HERCI)");
		lblId.setFont(new Font("SansSerif", Font.BOLD, 14));
		lblId.setForeground(textLblColor);
		topPanel.add(lblId);
		lblId.setMaximumSize(new Dimension(1600, 20));
		lblId.setMinimumSize(new Dimension(1600, 20));
	
		lblFramesDecoded = new JLabel(DECODED);
		lblFramesDecoded.setFont(new Font("SansSerif", Font.BOLD, 14));
		lblFramesDecoded.setBorder(new EmptyBorder(5, 2, 5, 5) );
		topPanel.add(lblFramesDecoded);

		centerPanel = new JPanel();
		centerPanel.setLayout(new BoxLayout(centerPanel, BoxLayout.X_AXIS));
		
		JPanel healthPanel = new JPanel();
		healthPanel.setLayout(new BoxLayout(healthPanel, BoxLayout.Y_AXIS));
		

		lblHSpayload = new JLabel();
		//lblHSpayload.setFont(new Font("SansSerif", Font.BOLD, 14));
		lblHSpayload.setBorder(new EmptyBorder(5, 2, 5, 5) );
		healthPanel.add(lblHSpayload);
		
		for (int r=0; r<displayRows; r++) {
			lblBytes[r] = new JLabel();
			healthPanel.add(lblBytes[r]);
		}
		
		//initDisplayHalves(centerPanel);

		
		splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT,
				healthPanel, centerPanel);
		splitPane.setOneTouchExpandable(true);
		splitPane.setContinuousLayout(true); // repaint as we resize, otherwise we can not see the moved line against the dark background

		if (splitPaneHeight != 0) 
			splitPane.setDividerLocation(splitPaneHeight);
		else
			splitPane.setDividerLocation(DEFAULT_DIVIDER_LOCATION);
		
		SplitPaneUI spui = splitPane.getUI();
	    if (spui instanceof BasicSplitPaneUI) {
	      // Setting a mouse listener directly on split pane does not work, because no events are being received.
	      ((BasicSplitPaneUI) spui).getDivider().addMouseListener(new MouseAdapter() {
	          public void mouseReleased(MouseEvent e) {
	        	  splitPaneHeight = splitPane.getDividerLocation();
	        	  Log.println("SplitPane: " + splitPaneHeight);
	      		Config.saveGraphIntParam(fox.getIdString(), HERCITAB, "splitPaneHeight", splitPaneHeight);
	          }
	      });
	    }
		//Provide minimum sizes for the two components in the split pane
		Dimension minimumSize = new Dimension(100, 50);
		healthPanel.setMinimumSize(minimumSize);
		centerPanel.setMinimumSize(minimumSize);
		add(splitPane, BorderLayout.CENTER);
		
		addBottomFilter();

		addTables();

	}

	private void addTables() {
		radTableModel = new RadiationTableModel();
		
		table = new JTable(radTableModel);
		table.setAutoCreateRowSorter(true);
		
		scrollPane = new JScrollPane (table, 
				   JScrollPane.VERTICAL_SCROLLBAR_ALWAYS, JScrollPane.HORIZONTAL_SCROLLBAR_ALWAYS);
		table.setFillsViewportHeight(true);
		table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
		//table.setMinimumSize(new Dimension(6200, 6000));
		centerPanel.add(scrollPane);

		TableColumn column = null;
		column = table.getColumnModel().getColumn(0);
		column.setPreferredWidth(45);
		
		column = table.getColumnModel().getColumn(1);
		column.setPreferredWidth(55);
		
		for (int i=0; i<58; i++) {
			column = table.getColumnModel().getColumn(i+2);
			column.setPreferredWidth(25);
		}

	}
	
	private void showHighSpeed() {
		lblHSpayload.setText("Last HERCI EXPERIMENT HIGH SPEED PAYLOAD: " + PayloadHERCIhighSpeed.MAX_PAYLOAD_SIZE + " bytes. Reset:" + hsPayload.getResets() + " Uptime:" + hsPayload.getUptime() );
		String s = "";
		int row = 0;
		for (int i =0; i < PayloadHERCIhighSpeed.MAX_PAYLOAD_SIZE; i++) {
			s = s + Decoder.plainhex(hsPayload.fieldValue[i]) + " ";
			// Print 32 bytes in a row
			if ((i+1)%32 == 0) {
				lblBytes[row++].setText(s);
				s = "";
			}
		}
	}
	
	protected void parseRadiationFrames() {
		String[][] data = Config.payloadStore.getRadData(SAMPLES, fox.foxId, START_RESET, START_UPTIME);
		if (data.length > 0)
			radTableModel.setData(parseRawBytes(data));
	}
	
	
	private void displayFramesDecoded(int u) {
		lblFramesDecoded.setText(DECODED + u);
	}

	@Override
	public void run() {
		running = true;
		done = false;
		while(running) {

			try {
				Thread.sleep(500); // refresh data once a second
			} catch (InterruptedException e) {
				Log.println("ERROR: HERCI thread interrupted");
				e.printStackTrace(Log.getWriter());
			}
			if (foxId != 0)
				if (Config.payloadStore.getUpdatedRad(foxId)) {
					radPayload = Config.payloadStore.getLatestRad(foxId);
					Config.payloadStore.setUpdatedRad(foxId, false);
					parseRadiationFrames();
					MainWindow.setTotalDecodes();
				}
				if (Config.payloadStore.getUpdatedHerci(foxId)) {
					
					hsPayload = Config.payloadStore.getLatestHerci(foxId);
					Config.payloadStore.setUpdatedHerci(foxId, false);

					if (hsPayload != null)
						showHighSpeed();
					
					displayFramesDecoded(Config.payloadStore.getNumberOfHerciFrames(foxId));
					MainWindow.setTotalDecodes();
				}
			
			/*
				if (Config.payloadStore.getUpdatedHerci(foxId)) {
					
					hsPayload = Config.payloadStore.getLatestHerci(foxId);
					Config.payloadStore.setUpdatedHerci(foxId, false);

					if (hsPayload != null)
						showHighSpeed();
					
					displayFramesDecoded(Config.payloadStore.getNumberOfHerciFrames(foxId));
					MainWindow.setTotalDecodes();
				}
			*/
			
		}
		done = true;
	}

	@Override
	public void itemStateChanged(ItemEvent e) {
		//Object source = e.getItemSelectable();
	
	}
	
	@Override
	public void actionPerformed(ActionEvent e) {
		super.actionPerformed(e);
	}
}
