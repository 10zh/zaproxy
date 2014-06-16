/*
 *
 * Paros and its related class files.
 * 
 * Paros is an HTTP/HTTPS proxy for assessing web application security.
 * Copyright (C) 2003-2004 Chinotec Technologies Company
 * 
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the Clarified Artistic License
 * as published by the Free Software Foundation.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * Clarified Artistic License for more details.
 * 
 * You should have received a copy of the Clarified Artistic License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */
// ZAP: 2011/05/15 Support for exclusions
// ZAP: 2011/09/06 Fix alert save plus concurrent mod exceptions
// ZAP: 2011/11/04 Correct getHierarchicNodeName
// ZAP: 2011/11/29 Added blank image to node names to fix redrawing issue
// ZAP: 2012/02/11 Re-ordered icons, added spider icon and notify via SiteMap 
// ZAP: 2012/03/11 Issue 280: Escape URLs in sites tree
// ZAP: 2012/03/15 Changed the method toString to use the class StringBuilder 
//      and reworked the method toString and getIcons. Renamed the method 
//      getIcons to appendIcons.
// ZAP: 2012/07/29 Issue 43: Added support for Scope
// ZAP: 2012/08/29 Issue 250 Support for authentication management
// ZAP: 2012/10/02 Issue 385: Added support for Contexts
// ZAP: 2013/01/23 Ignore Active scanner history refs
// ZAP: 2013/08/23 Make sure #nodeChanged() is called after removing a custom icon
// ZAP: 2013/11/16 Issue 869: Differentiate proxied requests from (ZAP) user requests
// ZAP: 2014/03/23 Issue 1084: NullPointerException while selecting a node in the "Sites" tab
// ZAP: 2014/04/10 Do not allow to set the parent node as itself
// ZAP: 2014/04/10 Issue 1118: Alerts Tab can get out of sync
// ZAP: 2014/05/05 Issue 1181: Vulnerable pages active scanned only once
// ZAP: 2014/05/23 Issue 1209: Reliability becomes Confidence and add levels
// ZAP: 2014/06/16 Fixed an issue in SiteNode#setHistoryReference(HistoryReference) that led
// to multiple occurrences of same HistoryReference(s) in the pastHistoryList.


package org.parosproxy.paros.model;

import java.awt.EventQueue;
import java.util.ArrayList;
import java.util.List;
import java.util.Vector;

import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.MutableTreeNode;

import org.apache.commons.lang.StringEscapeUtils;
import org.apache.log4j.Logger;
import org.parosproxy.paros.Constant;
import org.parosproxy.paros.core.scanner.Alert;

public class SiteNode extends DefaultMutableTreeNode {

	private static final long serialVersionUID = 7987615016786179150L;

	private String nodeName = null;
    private HistoryReference historyReference = null;
    private Vector<HistoryReference> pastHistoryList = new Vector<>(10);
	// ZAP: Support for linking Alerts to SiteNodes
    private SiteMap siteMap = null;
	private ArrayList<Alert> alerts = new ArrayList<>();
	private boolean justSpidered = false;
	//private boolean justAJAXSpidered = false;
	private ArrayList<String> icons = null;
	private ArrayList<Boolean> clearIfManual = null;

    private static Logger log = Logger.getLogger(SiteNode.class);
    private boolean isIncludedInScope = false;
    private boolean isExcludedFromScope = false;
	
    public SiteNode(SiteMap siteMap, int type, String nodeName) {
        super();
        this.siteMap = siteMap;
		this.nodeName = nodeName;
		this.icons = new ArrayList<>();
		this.clearIfManual = new ArrayList<>();
		if (type == HistoryReference.TYPE_SPIDER) {
			this.justSpidered = true;
		}
	}
    
    public void setCustomIcons(ArrayList<String> i, ArrayList<Boolean> c) {
    	this.icons = i;
    	this.clearIfManual = c;
    }
    
    public void addCustomIcon(String resourceName, boolean clearIfManual) {
    	if (! this.icons.contains(resourceName)) {
	    	this.icons.add(resourceName);
	    	this.clearIfManual.add(clearIfManual);
	    	this.nodeChanged();
    	}
    }
    
    public void removeCustomIcon(String resourceName) {
    	if (this.icons.contains(resourceName)) {
    		int i = this.icons.indexOf(resourceName);
    		this.icons.remove(i);
    		this.clearIfManual.remove(i);
    		this.nodeChanged();
    	}
    }
    
    private void appendIcons(StringBuilder sb) {
    	int highestRisk = -1;
    	Alert highestAlert = null;
    	for (Alert alert : this.getAlerts()) {
    		if (alert.getConfidence() != Alert.FALSE_POSITIVE && alert.getRisk() > highestRisk) {
    			highestRisk = alert.getRisk();
    			highestAlert = alert;
    		}
    	}
    	if (highestAlert != null) {
    		sb.append("&nbsp;<img src=\"");
    		sb.append(highestAlert.getIconUrl());
    		sb.append("\">&nbsp;");
    	}
    	if (justSpidered) {
        	sb.append("&nbsp;<img src=\"");
        	sb.append(Constant.class.getResource("/resource/icon/10/spider.png"));
        	sb.append("\">&nbsp;");
    	}
    	if (!this.icons.isEmpty()) {
    		for(String icon : this.icons) {
    			sb.append("&nbsp;<img src=\"");
    			sb.append(Constant.class.getResource(icon));
    			sb.append("\">&nbsp;");
    		}
    	}
    }
    
    @Override
    public String toString() {
    	StringBuilder sb = new StringBuilder();
    	sb.append("<html><body>");
    	appendIcons(sb);
    	sb.append(StringEscapeUtils.escapeHtml(nodeName));
    	sb.append("</body></html>");
    	
    	return sb.toString();
    }
    
    public boolean isParentOf (String nodeName) {
    	if (nodeName == null) {
    		return false;
    	}
        return nodeName.compareTo(this.nodeName) < 0;
    }

    public String getNodeName() {
    	return this.nodeName;
    }
    
    public String getHierarchicNodeName() {
    	if (this.isRoot()) {
    		return "";
    	}
    	if (((SiteNode)this.getParent()).isRoot()) {
    		return this.getNodeName();
    	}
    	String nodeName = this.getNodeName();
    	if (this.isLeaf()) {
    		// Need to clean up
    		int colonIndex = nodeName.indexOf(":");
    		if (colonIndex > 0) {
    			// Strip the GET/POST etc off
    			nodeName = nodeName.substring(colonIndex+1);
    		}
    		int bracketIndex = nodeName.indexOf("(");
    		if (bracketIndex > 0) {
    			// Strip the param summary off
    			nodeName = nodeName.substring(0, bracketIndex);
    		}
    		int quesIndex = nodeName.indexOf("?");
    		if (quesIndex > 0) {
    			// Strip the parameters off
    			nodeName = nodeName.substring(0, quesIndex);
    		}
    	}
    	return ((SiteNode)this.getParent()).getHierarchicNodeName() + "/" + nodeName;
    }
    
    public HistoryReference getHistoryReference() {
        return historyReference;
    }
    
    /**
     * Set current node reference.
     * If there is any existing reference, delete if spider record.
     * Otherwise, put into past history list.
     * @param historyReference
     */
    public void setHistoryReference(HistoryReference historyReference) {
    	
        if (getHistoryReference() != null) {
//            if (getHistoryReference().getHistoryType() == HistoryReference.TYPE_SPIDER) {
//                getHistoryReference().delete();
//                getHistoryReference().setSiteNode(null);
//            } else if (!getPastHistoryReference().contains(historyReference)) {
//                getPastHistoryReference().add(getHistoryReference());
//            }
            
        	if (this.justSpidered && (historyReference.getHistoryType() == HistoryReference.TYPE_PROXIED ||
        	        historyReference.getHistoryType() == HistoryReference.TYPE_ZAP_USER)) {
        		this.justSpidered = false;
        		this.nodeChanged();
        	}
			// we remove the icons of the node that has to be cleaned when manually visiting them
			if (!this.icons.isEmpty() && (historyReference.getHistoryType() == HistoryReference.TYPE_PROXIED ||
			        historyReference.getHistoryType() == HistoryReference.TYPE_ZAP_USER)) {
				for (int i = 0; i < this.clearIfManual.size(); ++i) {
					if (this.clearIfManual.get(i) && this.icons.size() > i) {
						this.icons.remove(i);
						this.clearIfManual.remove(i);
					}
				}
        		this.nodeChanged();
    		}
            if (HistoryReference.TYPE_SCANNER == historyReference.getHistoryType()) {
                getPastHistoryReference().add(historyReference);
                return;
            }

            // above code commented as to always add all into past reference.  For use in scanner
            if (!getPastHistoryReference().contains(getHistoryReference())) {
                getPastHistoryReference().add(getHistoryReference());
            }
        }
        
        this.historyReference = historyReference;
        this.historyReference.setSiteNode(this);
    }    
    
    private void nodeChanged() {
    	if (this.siteMap == null) {
    		return;
    	}
        if (EventQueue.isDispatchThread()) {
        	nodeChangedEventHandler();
        } else {
            try {
                EventQueue.invokeLater(new Runnable() {
                    @Override
                    public void run() {
                    	nodeChangedEventHandler();
                    }
                });
            } catch (Exception e) {
                log.error(e.getMessage(), e);
            }
        }
    }

    private void nodeChangedEventHandler() {
		this.siteMap.nodeChanged(this);
    }
    
    public Vector<HistoryReference> getPastHistoryReference() {
        return pastHistoryList;
    }
    
    public boolean hasAlert(Alert alert) {
		for (Alert a : this.getAlerts()) {
			   if (a.equals(alert)) {
				   // We've already recorded it
				   return true;
			   }
		}
    	return false;
    }
    
    public void addAlert(Alert alert) {
    	if (this.hasAlert(alert)) {
    		return;
    	}
    	this.alerts.add(alert);
    	if (this.getParent() != null && this.getParent() instanceof SiteNode) {
 			((SiteNode)this.getParent()).addAlert(alert);
    	}
		this.nodeChanged();
    }
    
    public void updateAlert(Alert alert) {
		Alert foundAlert = null;
		for (Alert a : this.getAlerts()) {
			if (a.getAlertId() == alert.getAlertId()) {
				// Do the work outside of the loop to prevent a concurrent mod exception
				foundAlert = a;
				break;
			}
		}
		if (foundAlert != null) {
			this.alerts.remove(foundAlert);
			this.alerts.add(alert);
		 	if (this.getParent() != null && this.getParent() instanceof SiteNode) {
		 		((SiteNode)this.getParent()).updateAlert(alert);
		 	}
			
		}
    }
    
    @SuppressWarnings("unchecked")
	public List<Alert> getAlerts() {
    	// This is a shallow copy, but prevents a ConcurrentModificationException
 	   return (List<Alert>) this.alerts.clone();
    }
    
    private void clearChildAlert (Alert alert, SiteNode child) {
    	// Alerts are propagated up, which means when one is deleted we need to work out if it still
    	// is present in another child node
    	boolean removed = true;
    	alerts.remove(alert);
		if (this.getChildCount() > 0) {
	    	SiteNode c = (SiteNode) this.getFirstChild();
	    	while (c != null) {
	    		if (! c.equals(child)) {
	    			if (c.hasAlert(alert)) {
	    				alerts.add(alert);
	    				removed = false;
	    				break;
	    			}
	    		}
	    		c = (SiteNode) this.getChildAfter(c);
	    	}
		}
	 	if (removed && this.getParent() != null && this.getParent() instanceof SiteNode) {
	 		((SiteNode)this.getParent()).clearChildAlert(alert, this);
	 	}
    }

	public void deleteAlert(Alert alert) {
		alerts.remove(alert);
		
		// Remove from parents, if not in siblings
	 	if (this.getParent() != null && this.getParent() instanceof SiteNode) {
	 		((SiteNode)this.getParent()).clearChildAlert(alert, this);
	 	}
		this.nodeChanged();
	}
	
    public void deleteAlerts(List<Alert> alerts) {
        List<Alert> alertsToRemove = new ArrayList<>(alerts);
        if (this.alerts.removeAll(alertsToRemove)) {
            // Remove from parents, if not in siblings
            if (this.getParent() != null && this.getParent() instanceof SiteNode) {
                ((SiteNode) this.getParent()).clearChildAlerts(alertsToRemove);
            }
            this.nodeChanged();
        }
    }

    private void clearChildAlerts(List<Alert> alerts) {
        List<Alert> alertsToRemove = new ArrayList<>(alerts);
        if (this.getChildCount() > 0) {
            SiteNode c = (SiteNode) this.getFirstChild();
            while (c != null) {
                alertsToRemove.removeAll(c.alerts);
                c = (SiteNode) this.getChildAfter(c);
            }
        }
        boolean changed = this.alerts.removeAll(alertsToRemove);
        if (changed) {
            if (this.getParent() != null && this.getParent() instanceof SiteNode) {
                ((SiteNode) this.getParent()).clearChildAlerts(alertsToRemove);
            }
            nodeChangedEventHandler();
        }
    }

	public boolean hasHistoryType (int type) {
		if (this.historyReference == null) {
			return false;
		}
		if (this.historyReference.getHistoryType() == type) {
			return true;
		}
		for (HistoryReference href : this.pastHistoryList) {
			if (href.getHistoryType() == type) {
				return true;
			}
		}
		return false;
	}
	
	public boolean hasJustHistoryType (int type) {
		if (this.historyReference == null) {
			return false;
		}
		if (this.historyReference.getHistoryType() != type) {
			return false;
		}
		for (HistoryReference href : this.pastHistoryList) {
			if (href.getHistoryType() != type) {
				return false;
			}
		}
		return true;
	}

	public boolean isIncludedInScope() {
		return isIncludedInScope;
	}

	public void setIncludedInScope(boolean isIncludedInScope, boolean applyToChildNodes) {
		this.isIncludedInScope = isIncludedInScope;
		this.nodeChanged();
		// Recurse down
		if (this.getChildCount() > 0 && applyToChildNodes) {
			SiteNode c = (SiteNode) this.getFirstChild();
    		while (c != null) {
    			c.setIncludedInScope(isIncludedInScope, applyToChildNodes);
    			c = (SiteNode) this.getChildAfter(c);
    		}
		}
	}

	public boolean isExcludedFromScope() {
		return isExcludedFromScope;
	}

	public void setExcludedFromScope(boolean isExcludedFromScope, boolean applyToChildNodes) {
		this.isExcludedFromScope = isExcludedFromScope;
		if (isExcludedFromScope) {
			this.isIncludedInScope = false;
		}
		this.nodeChanged();
		// Recurse down
		if (this.getChildCount() > 0 && applyToChildNodes) {
	    	SiteNode c = (SiteNode) this.getFirstChild();
	    	while (c != null) {
	    		c.setExcludedFromScope(isExcludedFromScope, applyToChildNodes);
	    		c = (SiteNode) this.getChildAfter(c);
	    	}
		}
	}
	
	@Override
	public void setParent(MutableTreeNode newParent) {
		if (newParent == this) {
			return;
		}
		super.setParent(newParent);
	}
}
