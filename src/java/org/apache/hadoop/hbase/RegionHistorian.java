/**
 * Copyright 2008 The Apache Software Foundation
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.hadoop.hbase;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.GregorianCalendar;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.hbase.client.HTable;
import org.apache.hadoop.hbase.io.BatchUpdate;
import org.apache.hadoop.hbase.io.Cell;
import org.apache.hadoop.hbase.util.Bytes;
/**
 * The Region Historian task is to keep track of every modification a region
 * has to go trought. Public methods are used to update the information in the
 * .META. table and to retreive it.
 */
public class RegionHistorian implements HConstants {

  static final Log LOG = LogFactory.getLog(RegionHistorian.class);

  private HTable metaTable;

  private GregorianCalendar cal = new GregorianCalendar();

  /** Singleton reference */
  private static RegionHistorian historian;

  /** Date formater for the timestamp in RegionHistoryInformation */
  private static SimpleDateFormat dateFormat = new SimpleDateFormat(
  "EEE, d MMM yyyy HH:mm:ss");

  public static enum HistorianColumnKey  {
    REGION_CREATION ( Bytes.toBytes(COLUMN_FAMILY_HISTORIAN_STR+"creation")),
    REGION_OPEN ( Bytes.toBytes(COLUMN_FAMILY_HISTORIAN_STR+"open")),
    REGION_SPLIT ( Bytes.toBytes(COLUMN_FAMILY_HISTORIAN_STR+"split")),
    REGION_COMPACTION ( Bytes.toBytes(COLUMN_FAMILY_HISTORIAN_STR+"compaction")),
    REGION_FLUSH ( Bytes.toBytes(COLUMN_FAMILY_HISTORIAN_STR+"flush")),
    REGION_ASSIGNMENT ( Bytes.toBytes(COLUMN_FAMILY_HISTORIAN_STR+"assignment"));

    public byte[] key;

    HistorianColumnKey(byte[] key) {
      this.key = key;
    }
  } 

  /**
   * Default constructor. Initializes reference to .META. table
   *
   */
  private RegionHistorian() {
    HBaseConfiguration conf = new HBaseConfiguration();

    try {
      metaTable = new HTable(conf, META_TABLE_NAME);
      LOG.debug("Region historian is ready.");
    } catch (IOException ioe) {
      LOG.warn("Unable to create RegionHistorian", ioe);
    }
  }

  /**
   * Singleton method
   * 
   * @return The region historian
   */
  public static RegionHistorian getInstance() {
    if (historian == null) {
      historian = new RegionHistorian();
    }
    return historian;
  }

  /**
   * Returns, for a given region name, an ordered list by timestamp of all
   * values in the historian column of the .META. table.
   * 
   * @param regionName
   *          Region name as a string
   * @return List of RegionHistoryInformation
   */
  public static List<RegionHistoryInformation> getRegionHistory(
      String regionName) {
    getInstance();
    List<RegionHistoryInformation> informations = new ArrayList<RegionHistoryInformation>();
    try {
      /*
       * TODO REGION_HISTORIAN_KEYS is used because there is no other for the
       * moment to retrieve all version and to have the column key information.
       * To be changed when HTable.getRow handles versions.
       */
      for (HistorianColumnKey keyEnu : HistorianColumnKey.values()) {
        byte[] columnKey = keyEnu.key;
        Cell[] cells = historian.metaTable.get(Bytes.toBytes(regionName),
            columnKey, ALL_VERSIONS);
        if (cells != null) {
          for (Cell cell : cells) {
            informations.add(historian.new RegionHistoryInformation(cell
                .getTimestamp(), Bytes.toString(columnKey).split(":")[1], Bytes
                .toString(cell.getValue())));
          }
        }
      }
    } catch (IOException ioe) {
      LOG.warn("Unable to retrieve region history", ioe);
    }
    Collections.sort(informations);
    return informations;
  }
  
  /**
   * Method to add a creation event to the row in the .META table
   * 
   * @param info
   */
  public static void addRegionAssignment(HRegionInfo info, String serverName) {

    add(HistorianColumnKey.REGION_ASSIGNMENT.key, "Region assigned to server "
        + serverName, info);
  }

  /**
   * Method to add a creation event to the row in the .META table
   * 
   * @param info
   */
  public static void addRegionCreation(HRegionInfo info) {

    add(HistorianColumnKey.REGION_CREATION.key, "Region creation", info);
  }

  /**
   * Method to add a opening event to the row in the .META table
   * 
   * @param info
   * @param address
   */
  public static void addRegionOpen(HRegionInfo info, HServerAddress address) {

    add(HistorianColumnKey.REGION_OPEN.key, "Region opened on server : "
        + address.getHostname(), info);
  }

  /**
   * Method to add a split event to the rows in the .META table with
   * information from oldInfo.
   * @param oldInfo
   * @param newInfo1 
   * @param newInfo2
   */
  public static void addRegionSplit(HRegionInfo oldInfo, HRegionInfo newInfo1,
      HRegionInfo newInfo2) {

    HRegionInfo[] infos = new HRegionInfo[] { newInfo1, newInfo2 };
    for (HRegionInfo info : infos) {
      add(HistorianColumnKey.REGION_SPLIT.key, "Region split from  : "
          + oldInfo.getRegionNameAsString(), info);
    }
  }

  /**
   * Method to add a compaction event to the row in the .META table
   * 
   * @param info
   */
  public static void addRegionCompaction(HRegionInfo info, String timeTaken) {
    if (LOG.isDebugEnabled()) {
      add(HistorianColumnKey.REGION_COMPACTION.key,
          "Region compaction completed in " + timeTaken, info);
    }
  }

  /**
   * Method to add a flush event to the row in the .META table
   * 
   * @param info
   */
  public static void addRegionFlush(HRegionInfo info, String timeTaken) {
    if (LOG.isDebugEnabled()) {
      add(HistorianColumnKey.REGION_FLUSH.key, "Region flush completed in "
          + timeTaken, info);
    }
  }

  /**
   * Method to add an event with LATEST_TIMESTAMP.
   * @param column
   * @param text
   * @param info
   */
  private static void add(byte[] column, String text, HRegionInfo info) {
    add(column, text, info, LATEST_TIMESTAMP);
  }

  /**
   * Method to add an event with provided information.
   * @param column
   * @param text
   * @param info
   * @param timestamp
   */
  private static void add(byte[] column, String text, HRegionInfo info, long timestamp) {
    if (!info.isMetaRegion()) {
      getInstance();
      BatchUpdate batch = new BatchUpdate(info.getRegionName());
      batch.setTimestamp(timestamp);
      batch.put(column, Bytes.toBytes(text));
      try {
        historian.metaTable.commit(batch);
      } catch (IOException ioe) {
        LOG.warn("Unable to '" + text + "'", ioe);
      }
    }
  }

  /**
   * Inner class that only contains information about an event.
   * 
   */
  public class RegionHistoryInformation implements
  Comparable<RegionHistoryInformation> {

    private long timestamp;

    private String event;

    private String description;

    public RegionHistoryInformation(long timestamp, String event,
        String description) {
      this.timestamp = timestamp;
      this.event = event;
      this.description = description;
    }

    /**
     * Returns the inverse value of Long.compareTo
     */
    public int compareTo(RegionHistoryInformation otherInfo) {
      return -1 * Long.valueOf(timestamp).compareTo(otherInfo.getTimestamp());
    }

    public String getEvent() {
      return event;
    }

    public String getDescription() {
      return description;
    }

    public long getTimestamp() {
      return timestamp;
    }

    /**
     * Returns the value of the timestamp processed
     * with the date formater.
     * @return
     */
    public String getTimestampAsString() {
      cal.setTimeInMillis(timestamp);
      return dateFormat.format(cal.getTime());
    }

  }

}
