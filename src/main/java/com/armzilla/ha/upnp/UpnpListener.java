package com.armzilla.ha.upnp;

import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.*;

/**
 * Created by arm on 4/11/15.
 */
@Component
public class UpnpListener {
    private Logger log = Logger.getLogger(UpnpListener.class);
    private static final int UPNP_DISCOVERY_PORT = 1900;
    private static final String UPNP_MULTICAST_ADDRESS = "239.255.255.250";

    @Value("${upnp.response.port}")
    private int upnpResponsePort;

    @Value("${server.port}")
    private int httpServerPort;

    @Value("${upnp.config.address}")
    private String responseAddress;

    @Autowired
    private ApplicationContext applicationContext;

    @Scheduled(fixedDelay = Integer.MAX_VALUE)
    public void startListening(){
        log.info("Starting UPNP Discovery Listener");

        try (DatagramSocket responseSocket = new DatagramSocket(upnpResponsePort);
             MulticastSocket upnpMulticastSocket  = new MulticastSocket(UPNP_DISCOVERY_PORT)) {

            InetAddress upnpGroupAddress = InetAddress.getByName(UPNP_MULTICAST_ADDRESS);
            upnpMulticastSocket.joinGroup(upnpGroupAddress);

            while(true){ //trigger shutdown here
                byte[] buf = new byte[1024];
                DatagramPacket packet = new DatagramPacket(buf, buf.length);
                upnpMulticastSocket.receive(packet);
                String packetString = new String(packet.getData());
                if(isSSDPDiscovery(packetString)){
                    log.debug("Got SSDP Discovery packet from " + packet.getAddress().getHostAddress() + ":" + packet.getPort());
                    sendUpnpResponse(responseSocket, packet.getAddress(), packet.getPort());
                }
            }
        }  catch (IOException e) {
            log.error("UpnpListener encountered an error. Shutting down", e);
            ConfigurableApplicationContext context = (ConfigurableApplicationContext) UpnpListener.this.applicationContext;
            context.close();

        }
        log.info("UPNP Discovery Listener Stopped");

    }

    /**
     * very naive ssdp discovery packet detection
     * @param body
     * @return
     */
    protected boolean isSSDPDiscovery(String body){
        if(body != null && body.startsWith("M-SEARCH * HTTP/1.1") && body.contains("MAN: \"ssdp:discover\"")){
            return true;
        }
        return false;
    }

    String discoveryTemplate = "HTTP/1.1 200 OK\r\n" +
            "CACHE-CONTROL: max-age=86400\r\n" +
            "EXT:\r\n" +
            "LOCATION: http://%s:%s/upnp/amazon-ha-bridge/setup.xml\r\n" +
            "OPT: \"http://schemas.upnp.org/upnp/1/0/\"; ns=01\r\n" +
            "01-NLS: %s\r\n" +
            "ST: urn:Belkin:device:**\r\n" +
            "USN: uuid:Socket-1_0-221438K0100073::urn:Belkin:device:**\r\n\r\n";
    protected void sendUpnpResponse(DatagramSocket socket, InetAddress requester, int sourcePort) throws IOException {
        String discoveryResponse = String.format(discoveryTemplate, responseAddress, httpServerPort, getRandomUUIDString());
        DatagramPacket response = new DatagramPacket(discoveryResponse.getBytes(), discoveryResponse.length(), requester, sourcePort);
        socket.send(response);
    }

    protected String getRandomUUIDString(){
        return "88f6698f-2c83-4393-bd03-cd54a9f8595"; // https://xkcd.com/221/
    }
}
