/*
 * Copyright 2011-2015 L2EMU UNIQUE
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.l2emuproject.proxy.ui.javafx.packet.view;

import static net.l2emuproject.util.ISODateTime.ISO_DATE_TIME_ZONE_MS;

import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.io.IOException;
import java.net.URL;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.TreeSet;

import org.apache.commons.lang3.tuple.Pair;
import org.google.jhsheets.filtered.FilteredTableView;
import org.google.jhsheets.filtered.operators.StringOperator;
import org.google.jhsheets.filtered.tablecolumn.ColumnFilterEvent;
import org.google.jhsheets.filtered.tablecolumn.FilterableStringTableColumn;

import net.l2emuproject.lang.L2TextBuilder;
import net.l2emuproject.network.mmocore.MMOBuffer;
import net.l2emuproject.network.protocol.IProtocolVersion;
import net.l2emuproject.network.protocol.ProtocolVersionManager;
import net.l2emuproject.proxy.io.conversion.ToPlaintextVisitor;
import net.l2emuproject.proxy.io.definitions.VersionnedPacketTable;
import net.l2emuproject.proxy.network.EndpointType;
import net.l2emuproject.proxy.network.meta.container.OpcodeOwnerSet;
import net.l2emuproject.proxy.state.entity.context.ICacheServerID;
import net.l2emuproject.proxy.state.entity.context.ServerSocketID;
import net.l2emuproject.proxy.ui.ReceivedPacket;
import net.l2emuproject.proxy.ui.i18n.UIStrings;
import net.l2emuproject.proxy.ui.javafx.packet.IPacketHidingConfig;
import net.l2emuproject.proxy.ui.javafx.packet.Packet2Html;
import net.l2emuproject.proxy.ui.javafx.packet.PacketHidingConfig;
import net.l2emuproject.proxy.ui.javafx.packet.PacketLogEntry;
import net.l2emuproject.proxy.ui.javafx.packet.ProtocolPacketHidingManager;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.SortedList;
import javafx.css.PseudoClass;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.util.Duration;

/**
 * A controller that manages a single packet log tab.
 * 
 * @author _dev_
 */
public class PacketLogTabController implements Initializable
{
	@FXML
	private Button _btnClearTable;
	
	@FXML
	private Button _btnClearMemory;
	
	@FXML
	private Label _labTablePacketCount;
	
	@FXML
	private Label _labMemoryPacketCount;
	
	@FXML
	private FilteredTableView<PacketLogEntry> _tvPackets;
	
	@FXML
	private TableColumn<PacketLogEntry, String> _colSender;
	
	@FXML
	private TableColumn<PacketLogEntry, String> _colOpcode;
	
	@FXML
	private FilterableStringTableColumn<PacketLogEntry, String> _colName;
	
	@FXML
	private PacketInterpViewController _packetDisplayController;
	
	@FXML
	private CheckMenuItem _cmiIgnoreFilters;
	
	@FXML
	private Menu _mHidePacket;
	
	@FXML
	private MenuItem _miHidePacketInProtocol;
	
	private static final int AUTO_SCROLL_THRESHOLD = 250;
	
	private final ObservableList<PacketLogEntry> _memoryPackets, _tablePackets;
	
	private final ObjectProperty<IProtocolVersion> _protocolProperty;
	private ICacheServerID _entityCacheContext;
	
	private BooleanProperty _scrollLockProperty;
	private boolean _autoScrollPending;
	
	private IPacketHidingConfig _packetHidingConfig;
	
	/** Creates this controller. */
	public PacketLogTabController()
	{
		_memoryPackets = FXCollections.observableArrayList();
		_tablePackets = FXCollections.observableArrayList();
		
		_protocolProperty = new SimpleObjectProperty<>(ProtocolVersionManager.getInstance().getFallbackProtocolGame());
		_protocolProperty.addListener((obs, old, neu) ->
		{
			if (neu == null)
				return;
			
			final Map<EndpointType, Set<byte[]>> tabConfig = _packetHidingConfig.getSaveableFormat();
			_packetHidingConfig = new PacketHidingConfig(ProtocolPacketHidingManager.getInstance().getHidingConfiguration(neu), tabConfig.get(EndpointType.CLIENT), tabConfig.get(EndpointType.SERVER));
			
			for (final PacketLogEntry e : _memoryPackets)
				e.updateView(neu);
			_tvPackets.refresh();
		});
		_scrollLockProperty = new SimpleBooleanProperty(false);
		// will only be modified on the UI thread
		_packetHidingConfig = new PacketHidingConfig(new TreeSet<>(OpcodeOwnerSet::comparePacketPrefixes), new TreeSet<>(OpcodeOwnerSet::comparePacketPrefixes));
	}
	
	@Override
	public void initialize(URL location, ResourceBundle resources)
	{
		final NumberFormat format = NumberFormat.getIntegerInstance(UIStrings.CURRENT_LOCALE);
		_labTablePacketCount.textProperty().bind(UIStrings.getEx("packettab.footer.count.table", Bindings.createStringBinding(() -> format.format(_tablePackets.size()), _tablePackets)));
		_labMemoryPacketCount.textProperty().bind(UIStrings.getEx("packettab.footer.count.memory", Bindings.createStringBinding(() -> format.format(_memoryPackets.size()), _memoryPackets)));
		
		final PseudoClass clientPacketRowClass = PseudoClass.getPseudoClass("client");
		_tvPackets.setRowFactory(tv ->
		{
			final TableRow<PacketLogEntry> row = new TableRow<>();
			row.itemProperty().addListener((obs, old, neu) -> row.pseudoClassStateChanged(clientPacketRowClass, neu != null ? neu.getPacket().getEndpoint().isClient() : false));
			return row;
		});
		
		final ContextMenu contextMenu = _tvPackets.getContextMenu();
		_tvPackets.setContextMenu(null);
		_tvPackets.getSelectionModel().selectedItemProperty().addListener((obs, old, neu) ->
		{
			if (neu == null)
			{
				_tvPackets.setContextMenu(null);
				return;
			}
			
			_mHidePacket.textProperty().bind(UIStrings.getEx("packettab.cmenu.hiding.menu", neu.nameProperty()));
			_miHidePacketInProtocol.textProperty().bind(UIStrings.getEx("packettab.cmenu.hiding.protocol", Bindings.convert(_protocolProperty)));
			_tvPackets.setContextMenu(contextMenu);
			
			//ServerListTypePublisher.LIST_TYPE.set(_owner.getServerListType());
			final Pair<String, String> html = Packet2Html.getHTML(neu.getPacket(), _protocolProperty.get(), _entityCacheContext);
			_packetDisplayController.setContent(html.getLeft(), html.getRight());
		});
		
		final SortedList<PacketLogEntry> sortableTablePackets = new SortedList<>(_tablePackets);
		sortableTablePackets.comparatorProperty().bind(_tvPackets.comparatorProperty());
		_tvPackets.setItems(sortableTablePackets);
		_tvPackets.addEventHandler(ColumnFilterEvent.FILTER_CHANGED_EVENT, e -> applyFilters());
		
		_colSender.setCellValueFactory(new PropertyValueFactory<>("sender"));
		_colOpcode.setCellValueFactory(new PropertyValueFactory<>("opcode"));
		_colName.setCellValueFactory(new PropertyValueFactory<>("name"));
		
		final Timeline tlAutoScroll = new Timeline(new KeyFrame(Duration.ZERO), new KeyFrame(Duration.millis(AUTO_SCROLL_THRESHOLD), e ->
		{
			if (!_autoScrollPending)
				return;
			
			_autoScrollPending = false;
			final int idx = _tvPackets.getItems().size() - 1;
			if (idx >= 0)
				_tvPackets.scrollTo(idx);
		}));
		tlAutoScroll.setCycleCount(Timeline.INDEFINITE);
		tlAutoScroll.play();
		
		_cmiIgnoreFilters.selectedProperty().addListener((obs, old, neu) -> applyFilters());
	}
	
	@FXML
	private void clearMemory(ActionEvent event)
	{
		clearTable(event);
		_memoryPackets.clear();
	}
	
	@FXML
	private void clearTable(ActionEvent event)
	{
		_tablePackets.clear();
	}
	
	@FXML
	public void copyPacketAsPlaintext(ActionEvent event)
	{
		final PacketLogEntry packetEntry = _tvPackets.getSelectionModel().getSelectedItem();
		if (packetEntry == null)
			return;
		
		Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(toPlaintext(Collections.singletonList(packetEntry))), null);
	}
	
	@FXML
	public void copyPacketAsXML(ActionEvent event)
	{
		final PacketLogEntry packetEntry = _tvPackets.getSelectionModel().getSelectedItem();
		if (packetEntry == null)
			return;
		
		Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(toXML(Collections.singletonList(packetEntry))), null);
	}
	
	@FXML
	public void copyVisiblePacketsAsPlaintext(ActionEvent event)
	{
		Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(toPlaintext(_tablePackets)), null);
	}
	
	@FXML
	public void copyVisiblePacketsAsXML(ActionEvent event)
	{
		Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(toXML(_tablePackets)), null);
	}
	
	@FXML
	private void hidePacketInTab(ActionEvent event)
	{
		hideSelectedPacket(_packetHidingConfig);
	}
	
	@FXML
	private void hidePacketInProtocol(ActionEvent event)
	{
		final IProtocolVersion protocol = _protocolProperty.get();
		hideSelectedPacket(ProtocolPacketHidingManager.getInstance().getHidingConfiguration(protocol));
		ProtocolPacketHidingManager.getInstance().markModified(protocol);
	}
	
	private void hideSelectedPacket(IPacketHidingConfig hidingConfig)
	{
		final PacketLogEntry packetEntry = _tvPackets.getSelectionModel().getSelectedItem();
		if (packetEntry == null)
			return;
		
		final ReceivedPacket packet = packetEntry.getPacket();
		final EndpointType endpoint = packet.getEndpoint();
		hidingConfig.setHidden(endpoint, VersionnedPacketTable.getInstance().getTemplate(_protocolProperty.get(), endpoint, packet.getBody()));
		
		applyFilters();
	}
	
	private boolean isHiddenByOpcode(PacketLogEntry packetEntry)
	{
		/*
		for (final StringOperator filter : _colOpcode.getFilters())
			if (isHidden(packetEntry.getOpcode(), filter))
				return true;
		*/
		return false;
	}
	
	private boolean isHiddenByName(PacketLogEntry packetEntry)
	{
		for (final StringOperator filter : _colName.getFilters())
			if (isHidden(packetEntry.getName(), filter))
				return true;
		
		return false;
	}
	
	private boolean isHiddenByDisplayConfig(PacketLogEntry packetEntry)
	{
		final ReceivedPacket packet = packetEntry.getPacket();
		final EndpointType endpoint = packet.getEndpoint();
		return _packetHidingConfig.isHidden(endpoint, VersionnedPacketTable.getInstance().getTemplate(_protocolProperty.get(), endpoint, packet.getBody()));
	}
	
	private boolean isHidden(String actualValue, StringOperator filter)
	{
		final String filterValue = filter.getValue();
		switch (filter.getType())
		{
			case EQUALS:
				if (!actualValue.equals(filterValue))
					return true;
				break;
			case NOTEQUALS:
				if (actualValue.equals(filterValue))
					return true;
				break;
			case CONTAINS:
				if (!actualValue.contains(filterValue))
					return true;
				break;
			case STARTSWITH:
				if (!actualValue.startsWith(filterValue))
					return true;
				break;
			case ENDSWITH:
				if (!actualValue.endsWith(filterValue))
					return true;
				break;
		}
		return false;
	}
	
	private String toPlaintext(List<PacketLogEntry> packets)
	{
		final L2TextBuilder sb = new L2TextBuilder();
		try
		{
			final MMOBuffer buf = new MMOBuffer();
			for (final PacketLogEntry packetEntry : packets)
				ToPlaintextVisitor.writePacket(packetEntry.getPacket(), _protocolProperty.get(), buf, _entityCacheContext, new SimpleDateFormat(ISO_DATE_TIME_ZONE_MS), sb);
			return sb.moveToString();
		}
		catch (IOException e)
		{
			// L2TB doesn't throw
			throw new AssertionError("L2TextBuilder", e);
		}
	}
	
	private String toXML(List<PacketLogEntry> packets)
	{
		final L2TextBuilder sb = new L2TextBuilder();
		try
		{
			final MMOBuffer buf = new MMOBuffer();
			for (final PacketLogEntry packetEntry : packets)
				ToPlaintextVisitor.writePacket(packetEntry.getPacket(), _protocolProperty.get(), buf, _entityCacheContext, new SimpleDateFormat(ISO_DATE_TIME_ZONE_MS), sb);
			return sb.moveToString();
		}
		catch (IOException e)
		{
			// L2TB doesn't throw
			throw new AssertionError("L2TextBuilder", e);
		}
	}
	
	/**
	 * Returns a binding that specifies whether the currently selected tab has a packet table with an entry selected.
	 * 
	 * @return if there is a selected packet
	 */
	public BooleanBinding anyPacketSelected()
	{
		return _tvPackets.getSelectionModel().selectedItemProperty().isNotNull();
	}
	
	/**
	 * Returns a binding that specifies whether the currently selected tab has a packet table with at least one packet (after all filters applied).
	 * 
	 * @return if there is a selected packet
	 */
	public BooleanBinding hasVisiblePackets()
	{
		return Bindings.size(_tablePackets).greaterThan(0);
	}
	
	/**
	 * Returns a binding that specifies whether the currently selected tab has a packet table with at least one packet (regardless of filters).
	 * 
	 * @return if there is a selected packet
	 */
	public BooleanBinding hasMemoryPackets()
	{
		return Bindings.size(_memoryPackets).greaterThan(0);
	}
	
	/**
	 * Returns the associated network protocol property.
	 * 
	 * @return protocol property
	 */
	public ObjectProperty<IProtocolVersion> protocolProperty()
	{
		return _protocolProperty;
	}
	
	/**
	 * Returns the packet hiding configuration associated with this tab.
	 * 
	 * @return packet hiding configuration
	 */
	public IPacketHidingConfig getPacketHidingConfig()
	{
		return _packetHidingConfig;
	}
	
	public void applyFilters()
	{
		if (_cmiIgnoreFilters.isSelected())
		{
			_tablePackets.setAll(_memoryPackets);
			return;
		}
		
		final ObservableList<PacketLogEntry> filterMatchingPackets = FXCollections.observableArrayList();
		for (final PacketLogEntry packetEntry : _memoryPackets)
			if (!isHiddenByDisplayConfig(packetEntry) && !isHiddenByOpcode(packetEntry) && !isHiddenByName(packetEntry))
				filterMatchingPackets.add(packetEntry);
		_tablePackets.setAll(filterMatchingPackets);
	}
	
	/**
	 * Associates an entity cache context.
	 * 
	 * @param entityCacheContext entity cache context
	 */
	public void setEntityCacheContext(ICacheServerID entityCacheContext)
	{
		_entityCacheContext = entityCacheContext;
		
		final boolean live = entityCacheContext instanceof ServerSocketID;
		_btnClearMemory.setDisable(!live);
		_btnClearTable.setDisable(!live);
	}
	
	/**
	 * Allows the automatic scrolldown behavior to be controlled for the packet table.
	 * 
	 * @param scrollLockProperty scroll lock
	 */
	public void installScrollLock(BooleanProperty scrollLockProperty)
	{
		if (scrollLockProperty == null)
			scrollLockProperty = new SimpleBooleanProperty(false);
		
		_scrollLockProperty = scrollLockProperty;
	}
	
	/**
	 * Adds a new packet to the underlying table view.
	 * 
	 * @param packet a packet
	 */
	public void addPacket(PacketLogEntry packet)
	{
		_memoryPackets.add(packet);
		
		if (isHiddenByDisplayConfig(packet) || isHiddenByOpcode(packet) || isHiddenByName(packet))
			return;
		
		_tablePackets.add(packet);
		_autoScrollPending = !_scrollLockProperty.get();
	}
}
