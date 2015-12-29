package telemetry;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.StringTokenizer;

import javax.swing.JOptionPane;

import common.Config;
import common.Log;
import common.Spacecraft;
import gui.MainWindow;

/**
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
 * This class is a flat file database for a single payload type.  It is referred to as a table, but
 * the actual data may be spread across several files on disk
 * 
 */
public class SatPayloadTable {

	public static final int MAX_DATA_LENGTH = 61;

	private String fileName;
	private SortedFramePartArrayList rtRecords;
	private boolean updated = false;

	public SatPayloadTable(int size, String name) throws FileNotFoundException {
		
		String dir = "";
        if (!Config.logFileDirectory.equalsIgnoreCase("")) {
			dir = Config.logFileDirectory + File.separator ;
			//System.err.println("Loading: "+log);
		}
        fileName = dir+name;
		rtRecords = new SortedFramePartArrayList(size);
		load(fileName);
	}
	
	public void setUpdated(boolean t) { updated = t; }
	public boolean getUpdated() { return updated; }
	
	public int getSize() { return rtRecords.size(); }
	
	public boolean hasFrame(int id, long uptime, int resets) { return rtRecords.hasFrame(id, uptime, resets); }
	
	public FramePart getLatest() {
		if (rtRecords.size() == 0) return null;
		return rtRecords.get(rtRecords.size()-1);
	}
	
	/**
	 * Return an array of payloads data with "period" entries for this sat id and from the given reset and
	 * uptime.
	 * @param period
	 * @param id
	 * @param fromReset
	 * @param fromUptime
	 * @return
	 */
	public String[][] getPayloadData(int period, int id, int fromReset, long fromUptime, int length) {
		int start = 0;
		int end = 0;
		
		if (fromReset == 0.0 && fromUptime == 0.0) { // then we take records nearest the end
			start = rtRecords.size()-period;
			end = rtRecords.size();
		} else {
			// we need to find the start point
			start = rtRecords.getNearestFrameIndex(id, fromUptime, fromReset);
			if (start == -1 ) start = rtRecords.size()-period;
			end = start + period;
		}
		if (end > rtRecords.size()) end = rtRecords.size();
		if (end < start) end = start;
		if (start < 0) start = 0;
		if (start > rtRecords.size()) start = rtRecords.size();
		
		int[][] results = new int[end-start][];
		String[] upTime = new String[end-start];
		String[] resets = new String[end-start];
		
		int j = results.length-1;
		for (int i=end-1; i>= start; i--) {
			//System.out.println(rtRecords.size());
			results[j] = rtRecords.get(i).getFieldValues();
			upTime[j] = ""+rtRecords.get(i).getUptime();
			resets[j--] = ""+rtRecords.get(i).getResets();
		}
		
		String[][] resultSet = new String[end-start][length];
		for (int r=0; r< end-start; r++) {
			resultSet[r][0] = resets[r];
			resultSet[r][1] = upTime[r];
			for (int k=0; k<results[r].length; k++)
				resultSet[r][k+2] = ""+results[r][k];
		}
		
		return resultSet;
	}
	
	/**
	 * Return a single field so that it can be graphed or analyzed
	 * @param name
	 * @param period
	 * @param fox
	 * @param fromReset
	 * @param fromUptime
	 * @return
	 */
	double[][] getGraphData(String name, int period, Spacecraft fox, int fromReset, long fromUptime) {

		int start = 0;
		int end = 0;
		
		if (fromReset == 0.0 && fromUptime == 0.0) { // then we take records nearest the end
			start = rtRecords.size()-period;
			end = rtRecords.size();
		} else {
			// we need to find the start point
			start = rtRecords.getNearestFrameIndex(fox.foxId, fromUptime, fromReset);
			if (start == -1 ) start = rtRecords.size()-period;
			end = start + period;
		}
		if (end > rtRecords.size()) end = rtRecords.size();
		if (end < start) end = start;
		if (start < 0) start = 0;
		if (start > rtRecords.size()) start = rtRecords.size();
		double[] results = new double[end-start];
		double[] upTime = new double[end-start];
		double[] resets = new double[end-start];
		
		int j = results.length-1;
		for (int i=end-1; i>= start; i--) {
			//System.out.println(rtRecords.size());
			if (Config.displayRawValues)
				results[j] = rtRecords.get(i).getRawValue(name);
			else
				results[j] = rtRecords.get(i).getDoubleValue(name, fox);
			upTime[j] = rtRecords.get(i).getUptime();
			resets[j--] = rtRecords.get(i).getResets();
		}
		
		double[][] resultSet = new double[3][end-start];
		resultSet[PayloadStore.DATA_COL] = results;
		resultSet[PayloadStore.UPTIME_COL] = upTime;
		resultSet[PayloadStore.RESETS_COL] = resets;
		
		return resultSet;
	}
	
	/**
	 * Save a new record to disk		
	 * @param f
	 */
	public boolean save(FramePart f) throws IOException {
		if (!rtRecords.hasFrame(f.id, f.uptime, f.resets)) {
			updated = true;
			save(f, fileName);
			return rtRecords.add(f);
			
		} else {
			if (Config.debugFrames) Log.println("DUPLICATE RECORD, not loaded");
		}
		return false;
	}
	
	/**
	 * Load a payload file from disk
	 * Payload files are stored in seperate logs, but this routine is written so that it can load mixed records
	 * from a single file
	 * @param log
	 * @throws FileNotFoundException
	 */
	public void load(String log) throws FileNotFoundException {
        String line;
        File aFile = new File(log );
		if(!aFile.exists()){
			try {
				aFile.createNewFile();
			} catch (IOException e) {
				JOptionPane.showMessageDialog(MainWindow.frame,
						e.toString(),
						"ERROR creating file " + log,
						JOptionPane.ERROR_MESSAGE) ;
				e.printStackTrace(Log.getWriter());
			}
		}
 
        BufferedReader dis = new BufferedReader(new FileReader(log));

        try {
        	while ((line = dis.readLine()) != null) {
        		if (line != null) {
        			StringTokenizer st = new StringTokenizer(line, ",");
        			String date = st.nextToken();
        			int id = Integer.valueOf(st.nextToken()).intValue();
        			int resets = Integer.valueOf(st.nextToken()).intValue();
        			long uptime = Long.valueOf(st.nextToken()).longValue();
        			int type = Integer.valueOf(st.nextToken()).intValue();
        			
        			// We should never get this situation, but good to check..
        			if (Config.satManager.getSpacecraft(id) == null) {
        				Log.errorDialog("FATAL", "Attempting to Load payloads from the Payload store for satellite with Fox Id: " + id 
        						+ "\n when no sattellite with that FoxId is configured.  Add this spacecraft to the satellite directory and restart FoxTelem."
        						+ "\nProgram will now exit");
        				System.exit(1);
        			}
        			FramePart rt = null;
        			if (type == FramePart.TYPE_REAL_TIME) {
        				rt = new PayloadRtValues(id, resets, uptime, date, st, Config.satManager.getRtLayout(id));
        			} else
        			if (type == FramePart.TYPE_MAX_VALUES) {
        				rt = new PayloadMaxValues(id, resets, uptime, date, st, Config.satManager.getMaxLayout(id));

        			} else
        			if (type == FramePart.TYPE_MIN_VALUES) {
        				rt = new PayloadMinValues(id, resets, uptime, date, st, Config.satManager.getMinLayout(id));
 
        			}
        			if (type == FramePart.TYPE_RAD_TELEM_DATA || type >= 700 && type < 800) {
        				rt = new RadiationTelemetry(id, resets, uptime, date, st, Config.satManager.getRadTelemLayout(id));
        			}
        			if (type == FramePart.TYPE_RAD_EXP_DATA || type >= 400 && type < 500) {
        				rt = new PayloadRadExpData(id, resets, uptime, date, st);
        			}        			
        			if (type == FramePart.TYPE_HERCI_HIGH_SPEED_DATA) {
        				rt = new PayloadHERCIhighSpeed(id, resets, uptime, date, st, Config.satManager.getHerciHSLayout(id));
        			}
        			if (type == FramePart.TYPE_HERCI_SCIENCE_HEADER ) {
        				rt = new RadiationTelemetry(id, resets, uptime, date, st, Config.satManager.getHerciHSHeaderLayout(id));
        			}
        			if (type == FramePart.TYPE_HERCI_HS_PACKET ) {
  //FIXME!!!      			//	rt = new HerciHighSpeedPacket(id, resets, uptime, date, st, Config.satManager.getHerciHSHeaderLayout(id));
        			}

        			if (rt != null) {
        				rtRecords.add(rt);
        			}
        		}
    			updated = true;
        	}
        	dis.close();
        } catch (IOException e) {
        	e.printStackTrace(Log.getWriter());
        	
        } catch (NumberFormatException n) {
        	n.printStackTrace(Log.getWriter());
        }
        
	}

	/**
	 * Save a payload to the log file
	 * @param frame
	 * @param log
	 * @throws IOException
	 */
	public void save(FramePart frame, String log) throws IOException {
		 
		File aFile = new File(log );
		if(!aFile.exists()){
			aFile.createNewFile();
		}
		//Log.println("Saving: " + log);
		//use buffering and append to the existing file
		Writer output = new BufferedWriter(new FileWriter(aFile, true));
		try {
			output.write( frame.toFile() + "\n" );

			output.flush();
		} finally {
			// Make sure it is closed even if we hit an error
			output.flush();
			output.close();
		}
	}
	
	public void remove() throws IOException {
		SatPayloadStore.remove(fileName);
	}
}
