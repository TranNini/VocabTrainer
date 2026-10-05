package vocabapp;

import org.springframework.boot.web.context.WebServerInitializedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

// Prints where to open the app once the server is running
@Component
public class StartupInfo implements ApplicationListener<WebServerInitializedEvent> {
    private final AccessCode accessCode;

    public StartupInfo(AccessCode accessCode) {
        this.accessCode = accessCode;
    }

    @Override
    public void onApplicationEvent(WebServerInitializedEvent event) {
        int port = event.getWebServer().getPort();
        System.out.println();
        System.out.println("=== Vocab Trainer is running ===");
        System.out.println("On this Mac:   http://localhost:" + port);
        List<String> addresses = wifiAddresses();
        if (addresses.isEmpty()) {
            System.out.println("On your iPhone: no Wi-Fi address found. Is the Mac connected to Wi-Fi?");
        }
        for (String address : addresses) {
            System.out.println("On your iPhone: http://" + address + ":" + port + "  (same Wi-Fi)");
        }
        System.out.println("Access code:   " + accessCode.getCode());
        System.out.println();
    }

    // The Mac's addresses in the home network, like 192.168.1.23
    private static List<String> wifiAddresses() {
        List<String> addresses = new ArrayList<>();
        try {
            for (NetworkInterface network : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!network.isUp() || network.isLoopback() || network.isVirtual()) {
                    continue;
                }
                for (InetAddress address : Collections.list(network.getInetAddresses())) {
                    if (address instanceof Inet4Address && address.isSiteLocalAddress()) {
                        addresses.add(address.getHostAddress());
                    }
                }
            }
        } catch (SocketException e) {
            // no addresses then; the Mac URL still works
        }
        return addresses;
    }
}
