package fiit.mclistener;

import java.net.Inet4Address;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.locks.StampedLock;

import org.pcap4j.core.NotOpenException;
import org.pcap4j.core.PacketListener;
import org.pcap4j.core.PcapHandle;
import org.pcap4j.core.PcapNativeException;
import org.pcap4j.core.PcapNetworkInterface;
import org.pcap4j.core.Pcaps;
import org.pcap4j.core.BpfProgram.BpfCompileMode;
import org.pcap4j.core.PcapNetworkInterface.PromiscuousMode;
import org.pcap4j.packet.EthernetPacket;
import org.pcap4j.packet.IpV4Packet;
import org.pcap4j.packet.IpV4Rfc1349Tos;
import org.pcap4j.packet.IpV4Packet.IpV4Header;
import org.pcap4j.packet.UdpPacket.UdpHeader;
import org.pcap4j.packet.Packet;
import org.pcap4j.packet.UdpPacket;
import org.pcap4j.packet.UnknownPacket;
import org.pcap4j.packet.namednumber.EtherType;
import org.pcap4j.packet.namednumber.IpNumber;
import org.pcap4j.packet.namednumber.IpVersion;
import org.pcap4j.packet.namednumber.UdpPort;
import org.pcap4j.util.MacAddress;

import fiit.flexbytes.FlexBytes;

public class PcapMachine {

	private static final SimpleDateFormat SDF = new SimpleDateFormat("HH:mm:ss.SSS");
	private static final String ERROR_INTERFACE = "Aa error occured during the polling of the network interface.";
	private static final String INVALID_MULTICAST = "The address must be a valid multicast address in IPv4 notation (for example 224.26.10.5).";
	private static final String DUPLICATE_ADDRESS = "The address has already been registered.";
	private static final String START_ERROR = "'";
	private static final String STOP_ERROR = "': ";
	private static final String ALL_ZERO_ADDRESS = "0.0.0.0";
	private static final String ALL_ROUTERS_ADDRESS = "224.0.0.2";
	private static final short UDP_LISTENNING_PORT = 780;
	private static final String FILTER = "(ether multicast && ip multicast && ip proto 2) "
			+ "|| (ether multicast && ip multicast && ip proto 17 && dst port " + UDP_LISTENNING_PORT + ")";
	private static final String CHARSET = "UTF-8";
	private static final int SNAPLEN = 65536;
	private static final int READ_TIMEOUT = 10;
		
	private final StampedLock lock = new StampedLock();
	private final LinkedHashSet<Inet4Address> registeredGroups = new LinkedHashSet<>();
	private final ExecutorService loopExecutor = Executors.newSingleThreadExecutor();
	private Future<?> loopThreadConnector;
 	private MacAddress sourceMacAddress;
	private Inet4Address sourceIpAddress;
	private Inet4Address zeroIpAddress;
	private Inet4Address allRoutersIpAddress;
	private Window window;
	private PcapHandle pcapHandler = null;
	private PcapNetworkInterface nif;
	
	public PcapMachine(Window window) throws SocketException, PcapNativeException, UnknownHostException {
		setWindow(window);		
		setSourceMacAddress();
		setSourceIpAddress();
		setZeroIpAddress();
		setAllRoutersIpAddress();
	}
	
	private void setAllRoutersIpAddress() throws UnknownHostException {
		allRoutersIpAddress = (Inet4Address) Inet4Address.getByName(ALL_ROUTERS_ADDRESS);
	}
	
	private void setZeroIpAddress() throws UnknownHostException {
		zeroIpAddress = (Inet4Address) Inet4Address.getByName(ALL_ZERO_ADDRESS);
	}
	
	private void setWindow(Window window) {
		this.window = window;
	}
	
	private void setSourceMacAddress() throws SocketException {
		this.sourceMacAddress = MacAddress.getByAddress(findNetworkInterface().getHardwareAddress());		
	}
	
	private void setSourceIpAddress() throws SocketException, PcapNativeException {
		this.sourceIpAddress = (Inet4Address)Collections.list(findNetworkInterface().getInetAddresses())
			 .stream()
			.filter(ip -> ip instanceof Inet4Address)
			.findAny()
			.get();
		nif = Pcaps.getDevByAddress(sourceIpAddress);
	}
	
	private NetworkInterface findNetworkInterface() throws SocketException {
		List<NetworkInterface> nets = Collections.list(NetworkInterface.getNetworkInterfaces());
		Optional<NetworkInterface> adapterExistence = nets.parallelStream().filter((net) -> {
			try {
				return !net.isLoopback() && !net.isVirtual() && net.getHardwareAddress() != null
						&& Collections.list(net.getInetAddresses()).parallelStream()
								.filter(ip -> ip instanceof Inet4Address).findAny().isPresent();
			} catch (Exception e) {
				return false;
			}
		}).findFirst();
		if (adapterExistence.isPresent()) {
			return adapterExistence.get();
		} else {
			throw new SocketException(ERROR_INTERFACE);
		}
	}
	
	public void registerMulticastGroup(String multicastGroup) 
			throws UnknownHostException, UnsupportedOperationException, PcapNativeException, 
			NotOpenException, ExceptionBuffer {
		try {
			long stamp = lock.writeLock();
			try {
				Inet4Address multicastGroupAddress = (Inet4Address) Inet4Address.getByName(multicastGroup);
				if(multicastGroupAddress.isMulticastAddress()) {
					boolean added = registeredGroups.add(multicastGroupAddress);
					if(added) {
						if(registeredGroups.size()==1) {
							startMachine();							
						}
						reportSpecificGroup(multicastGroupAddress);
					} else {
						throw new UnsupportedOperationException(START_ERROR + multicastGroup 
								+ STOP_ERROR + DUPLICATE_ADDRESS);
					}
				} else {
					throw new UnknownHostException(START_ERROR + multicastGroup 
							+ STOP_ERROR + INVALID_MULTICAST);
				}
			} finally {
				lock.unlock(stamp);
			}						
		} catch (UnknownHostException e) {
			throw new UnknownHostException(START_ERROR + multicastGroup 
					+ STOP_ERROR + INVALID_MULTICAST);
		}
	}
	
	public void removeMulticastGroup(String multicastGroup) throws UnknownHostException,
		NotOpenException, ExceptionBuffer, PcapNativeException {
		try {
			long stamp = lock.writeLock();
			try {
				Inet4Address multicastGroupAddress = (Inet4Address) Inet4Address.getByName(multicastGroup);
				boolean willBeRemoved = registeredGroups.contains(multicastGroupAddress);
				if(willBeRemoved) {
					leaveSpecificGroup(multicastGroupAddress);
				}
				if(willBeRemoved && registeredGroups.size()==1) {
					stopMachine();
				}
				registeredGroups.remove(multicastGroupAddress);
			} finally {
				lock.unlock(stamp);
			}
		} catch (UnknownHostException e) {
			throw new UnknownHostException(START_ERROR + multicastGroup + STOP_ERROR + INVALID_MULTICAST);
		}
	}
	
	private void startMachine() throws PcapNativeException, NotOpenException {
		pcapHandler = nif.openLive(SNAPLEN, PromiscuousMode.PROMISCUOUS, READ_TIMEOUT);
		pcapHandler.setFilter(FILTER, BpfCompileMode.OPTIMIZE);
		loopThreadConnector = loopExecutor.submit(() -> {
			try {			
				pcapHandler.loop(-1, new PacketListener() {				
					@Override
					public void gotPacket(Packet packet) {
						try {
							IpV4Packet ipv4Packet = packet.get(IpV4Packet.class);
							IpV4Header ipv4Header = ipv4Packet.getHeader();
							if(ipv4Header.getProtocol().equals(IpNumber.UDP)) {
								Inet4Address destinationAddress = ipv4Header.getDstAddr();
								if(registeredGroups.contains(destinationAddress)) {
									UdpPacket udpDatagram = (UdpPacket)packet.get(UdpPacket.class);
									UdpHeader udpHeader = udpDatagram.getHeader();
									UdpPort udpPort = udpHeader.getDstPort();									
									if(udpPort.equals(new UdpPort(UDP_LISTENNING_PORT,"multicast-spec"))) {
										byte[] rawData = udpDatagram.getPayload().getRawData();								
										StringBuilder message = new StringBuilder();
										message.append("time: '");
										message.append(SDF.format(new Date()));
										message.append("', from: '");
										message.append(destinationAddress.toString());
										message.append("', message: '");
										message.append(new String(rawData,CHARSET));
										window.addLineToMessagesArea(message.toString());
									}
								}
							} else if(ipv4Header.getProtocol().equals(IpNumber.IGMP)) {
								FlexBytes igmpData = new FlexBytes(ipv4Packet.getPayload().getRawData());
								Igmpv2Parser igmpMessage = new Igmpv2Parser(igmpData);
								if(igmpMessage.getType().equals(Igmpv2Type.QUERY) && igmpMessage.testChecksum()) {
									Inet4Address groupAddress = igmpMessage.getGroupAddress();
									if(groupAddress.equals(zeroIpAddress)) {
										reportAllgroups();
									} else if(registeredGroups.contains(groupAddress)) {
										reportSpecificGroup(groupAddress);
									}
								}
							}
						} catch(Exception ex) {
							System.err.println(ex.getMessage());
						}
					}
				});
			} catch (PcapNativeException | InterruptedException | NotOpenException ex) {
				Thread.currentThread().interrupt();
			}
		});
	}
	
	private void stopMachine() throws NotOpenException {
		pcapHandler.breakLoop();
		loopThreadConnector.cancel(true);
		pcapHandler.close();
	}
	
	private void reportSpecificGroup(Inet4Address groupAddress) throws ExceptionBuffer, 
		PcapNativeException, NotOpenException {
		Igmpv2Type type = Igmpv2Type.REPORT;
		int maxResponseTime = 0;		
		Igmpv2Builder igmp = new Igmpv2Builder(type, maxResponseTime, groupAddress);
		byte[] igmpData = igmp.build().createByteArrayImage();
		UnknownPacket.Builder igmpBuilder = new UnknownPacket.Builder();
		igmpBuilder.rawData(igmpData);		
		IpV4Packet.Builder ipv4Builder = new IpV4Packet.Builder();
		ipv4Builder.srcAddr(sourceIpAddress)
			.dstAddr(groupAddress)
			.version(IpVersion.IPV4)
			.protocol(IpNumber.IGMP)
			.tos(IpV4Rfc1349Tos.newInstance((byte) 0))
			.ttl((byte)1)
			.payloadBuilder(igmpBuilder)
			.correctChecksumAtBuild(true)
			.correctLengthAtBuild(true)
			.paddingAtBuild(true);		
		EthernetPacket.Builder ethernetBuilder = new EthernetPacket.Builder();
		ethernetBuilder.srcAddr(sourceMacAddress)
			.dstAddr(covertMulticastIpToMulticastMac(groupAddress))
			.type(EtherType.IPV4)
			.payloadBuilder(ipv4Builder)
			.paddingAtBuild(true);		
		pcapHandler.sendPacket(ethernetBuilder.build());
	}
	
	private void leaveSpecificGroup(Inet4Address groupAddress) throws ExceptionBuffer, 
		PcapNativeException, NotOpenException {
		Igmpv2Type type = Igmpv2Type.LEAVE;
		int maxResponseTime = 0;		
		Igmpv2Builder igmp = new Igmpv2Builder(type, maxResponseTime, groupAddress);
		byte[] igmpData = igmp.build().createByteArrayImage();
		UnknownPacket.Builder igmpBuilder = new UnknownPacket.Builder();
		igmpBuilder.rawData(igmpData);		
		IpV4Packet.Builder ipv4Builder = new IpV4Packet.Builder();
		ipv4Builder.srcAddr(sourceIpAddress)
			.dstAddr(allRoutersIpAddress)
			.version(IpVersion.IPV4)
			.protocol(IpNumber.IGMP)
			.tos(IpV4Rfc1349Tos.newInstance((byte) 0))
			.ttl((byte)1)
			.payloadBuilder(igmpBuilder)
			.correctChecksumAtBuild(true)
			.correctLengthAtBuild(true)
			.paddingAtBuild(true);		
		EthernetPacket.Builder ethernetBuilder = new EthernetPacket.Builder();
		ethernetBuilder.srcAddr(sourceMacAddress)
			.dstAddr(covertMulticastIpToMulticastMac(allRoutersIpAddress))
			.type(EtherType.IPV4)
			.payloadBuilder(ipv4Builder)
			.paddingAtBuild(true);		
		pcapHandler.sendPacket(ethernetBuilder.build());
	}
	
	private void reportAllgroups() throws ExceptionBuffer {
		ExceptionBuffer buffer = new ExceptionBuffer();
		registeredGroups.forEach(g -> {
			try {
				reportSpecificGroup(g);
			} catch (Exception e) {
				buffer.addException(e);
			}
		});
		buffer.throwIfItIsNeeded();
	}
	
	private MacAddress covertMulticastIpToMulticastMac(Inet4Address address) {
		byte[] finalAddress = new byte[6];
		byte[] ipBytes = address.getAddress();
		finalAddress[0] = 1;
		finalAddress[1] = 0;
		finalAddress[2] = 94;
		finalAddress[3] = (byte)(ipBytes[1]&127);
		finalAddress[4] = ipBytes[2];
		finalAddress[5] = ipBytes[3];
		return MacAddress.getByAddress(finalAddress);
	}
	
}
