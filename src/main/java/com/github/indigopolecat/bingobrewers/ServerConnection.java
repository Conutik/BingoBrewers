package com.github.indigopolecat.bingobrewers;

import java.io.IOException;
import com.esotericsoftware.kryonet.Client;
import com.esotericsoftware.kryonet.Connection;
import com.esotericsoftware.kryonet.Listener;
import com.esotericsoftware.minlog.Log;
import com.github.indigopolecat.kryo.KryoNetwork;
import com.github.indigopolecat.kryo.KryoNetwork.ReceivedString;
import com.github.indigopolecat.kryo.KryoNetwork.ResponseString;
import com.github.indigopolecat.kryo.KryoNetwork.SplashNotification;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.network.play.server.S45PacketTitle;
import net.minecraft.util.IChatComponent;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.Event;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import static com.esotericsoftware.minlog.Log.*;
import static java.lang.String.valueOf;

public class ServerConnection extends Listener implements Runnable {

    // The Hud renderer checks this every time it renders
    public static ArrayList<HashMap<String, ArrayList<String>>> mapList = new ArrayList<>();
    public static ArrayList<String> keyOrder = new ArrayList<>();
    int waitTime;
    boolean repeat;
    public static ArrayList<String> hubList = new ArrayList<>();
    long originalTime = -1;


    @Override
    public void run() {
        Client client1 = new Client();
        setClient(client1);
        if (bingoBrewers.client == null) {
            System.out.println("Client is null");
        }
        waitTime = 5000;
        System.out.println("Disconnected from server. Reconnecting in " + waitTime / 1000 + " seconds.");
        repeat = true;
        while (repeat) {
            try {
                connection();
            } catch (Exception e) {
                System.out.println("Server Connection Error: " + e.getMessage());
                bingoBrewers.client.close();
                try {
                    Thread.sleep(waitTime);
                } catch (InterruptedException ex) {
                    throw new RuntimeException(ex);
                }
                if (waitTime < 60000) {
                    waitTime *= (Math.random());
                }
            }
        }

    }

    private void connection() throws IOException {
        Log.set(LEVEL_ERROR);
        KryoNetwork.register(bingoBrewers.client);
        bingoBrewers.client.addListener(new Listener() {
            public void received(Connection connection, Object object) {
                if (object instanceof ReceivedString) {
                    ReceivedString request = (ReceivedString) object;
                    System.out.println(request.hello);
                    ResponseString response = new ResponseString();
                    response.hello = "Hello from the server!";
                    connection.sendTCP(response);
                } else if (object instanceof SplashNotification) {
                    System.out.println("Received splash notification");
                    boolean sendNotif = true;
                    SplashNotification notif = (SplashNotification) object;
                    // Remove the previous splash notification with the same ID (if message is edited)
                    for (int i = 0; i < mapList.size(); i++) {
                        HashMap<String, ArrayList<String>> map = mapList.get(i);
                        if (map.get("Splash").get(0).equals(notif.splash)) {
                            ArrayList<String> hubField = map.get("Hub");
                            // Don't send notification if the hub # or hub type (dungeon/normal) hasn't changed
                            try {
                                String hubNumber = hubField.get(1).replaceAll(": (\\d+).*", "$1");
                                if (hubNumber.equals(notif.message) && notif.dungeonHub == hubField.get(0).contains("Dungeon Hub")) {
                                    sendNotif = false;
                                    hubList.remove(hubNumber);
                                    hubList.remove("DH" + hubNumber);
                                }

                            } catch (Exception ignored) {}

                            // keep track of the original time the splash was sent, instead of updating each time it's edited
                            originalTime = Long.parseLong(map.get("Time").get(0));

                            mapList.remove(mapList.get(i));
                        }
                    }
                    updateMapList(notif, sendNotif);
                } else if (object instanceof KryoNetwork.PlayerCountBroadcast) {
                    KryoNetwork.PlayerCountBroadcast request = (KryoNetwork.PlayerCountBroadcast) object;
                    for (HashMap<String, ArrayList<String>> map : mapList) {
                        if (map.containsKey("Hub")) {
                            String hub = map.get("Hub").get(1).replaceAll(": (\\d+).*", "$1");
                            if (request.playerCounts.containsKey(hub)) {
                                // If the hub is a dungeon hub, it has a 24 player limit
                                if (map.get("Hub").get(0).equals("Dungeon Hub")) {
                                    map.get("Hub").set(1, ": " + hub + " (" + request.playerCounts.get(hub) + "/24)");
                                } else {
                                    map.get("Hub").set(1, ": " + hub + " (" + request.playerCounts.get(hub) + "/80)");
                                }
                            }
                        }
                    }
                }
            }


            @Override
            public void disconnected(Connection connection) {
                reconnect();
            }

        });
        bingoBrewers.client.start();
        bingoBrewers.client.connect(3000, "38.46.216.110", 8080, 7070);
        System.out.println("Connected to server.");
        ReceivedString request = new ReceivedString();
        request.hello = "Here is a request!";
        System.out.println("sending");
        bingoBrewers.client.sendTCP(request);
        System.out.println("sent");
        // List of all keys that may be used in infopanel, in the order they'll be rendered in an element
        keyOrder.clear(); // clear the list so it doesn't keep adding the same keys every time you reconnect
        keyOrder.add("Hub");
        keyOrder.add("Splasher");
        keyOrder.add("Party");
        keyOrder.add("Location");
        keyOrder.add("Note");
        repeat = false;
    }

    public synchronized void setClient(Client client) {
        bingoBrewers.client = client;
    }

    public synchronized Client getClient() {
        return bingoBrewers.client;

    }

    public synchronized void setActiveHud(TitleHud activeTitle) {
        bingoBrewers.activeTitle = activeTitle;
    }

    public synchronized TitleHud getActiveHud() {
        return bingoBrewers.activeTitle;
    }

    public void updateMapList(SplashNotification notif, boolean sendNotif) {
        String hub = notif.message;
        String splasher = notif.splasher;
        String partyHost = notif.partyHost;
        if (!partyHost.equals("No Party")) {
            partyHost = "/p join " + partyHost;
        }
        List<String> note = notif.note;
        // This should always be "Bea House" but is hard coded server side incase it ever needs to change quickly
        String location = notif.location;

        HashMap<String, ArrayList<String>> splashInfo = new HashMap<String, ArrayList<String>>();

        ArrayList<String> hubInfo = new ArrayList<>();
        if (!notif.dungeonHub) {
            hubInfo.add("Hub");
            hubList.add(hub);
        } else {
            hubInfo.add("Dungeon Hub");
            // Identify a hub as a dungeonhub to avoid mixing up regular hubs and dhubs
            hubList.add("DH" + hub);
        }
        hubInfo.add(": " + hub);
        splashInfo.put("Hub", hubInfo);

        ArrayList<String> splasherInfo = new ArrayList<>();
        splasherInfo.add("Splasher");
        splasherInfo.add(": " + splasher);
        splashInfo.put("Splasher", splasherInfo);

        ArrayList<String> partyInfo = new ArrayList<>();
        partyInfo.add("Bingo Party");
        partyInfo.add(": " + partyHost);
        splashInfo.put("Party", partyInfo);

        ArrayList<String> locationInfo = new ArrayList<>();
        locationInfo.add("Location");
        locationInfo.add(": " + location);
        splashInfo.put("Location", locationInfo);

        ArrayList<String> noteInfo = new ArrayList<>();
        noteInfo.add("Note");
        if (note.isEmpty()) {
            noteInfo.add(": No Note");
        } else {
            noteInfo.add(": ");
            noteInfo.addAll(note);
        }
        splashInfo.put("Note", noteInfo);

        ArrayList<String> timeInfo = new ArrayList<>();
        if (originalTime != -1) {
            timeInfo.add(valueOf(originalTime));
            originalTime = -1;
        } else {
            timeInfo.add(valueOf(System.currentTimeMillis()));
        }
        splashInfo.put("Time", timeInfo);
        ArrayList<String> splashId = new ArrayList<>();
        splashId.add(notif.splash);
        splashInfo.put("Splash", splashId);

        mapList.add(splashInfo);
        if (sendNotif) {
            notification(hub, notif.dungeonHub);
        }
    }

    public void notification(String hub, boolean dungeonHub) {
        EntityPlayerSP player = Minecraft.getMinecraft().thePlayer;
        if (!dungeonHub) {
            TitleHud titleHud = new TitleHud("Splash in Hub " + hub, 0x8BAFE0, 4000);
            setActiveHud(titleHud);
        } else {
            TitleHud titleHud = new TitleHud("Splash in Dungeon Hub " + hub, 0x8BAFE0, 4000);
            setActiveHud(titleHud);
        }

        player.playSound("bingobrewers:splash_notification", 1.0f, 1.0f);
    }

    public synchronized void sendPlayerCount(KryoNetwork.PlayerCount count) {
        Client currentClient = getClient();
        if (currentClient == null) {
            System.out.println("Client is null");
            return;
        }
        currentClient.sendUDP(count);
    }

    public void reconnect() {
        bingoBrewers.client.close();
        waitTime = (int) (5000 * Math.random());
        System.out.println("Disconnected from server. Reconnecting in " + waitTime + " milliseconds.");
        repeat = true;
        while (repeat) {
            try {
                bingoBrewers.client = new Client();
                connection();
            } catch (Exception e) {
                System.out.println("Server Connection Error: " + e.getMessage());
                try {
                    Thread.sleep(waitTime);
                } catch (InterruptedException ex) {
                    throw new RuntimeException(ex);
                }
                if (waitTime < 60000) {
                    waitTime *= 2;
                } else if (waitTime > 60000) {
                    waitTime = 60000;
                }
                System.out.println("Disconnected from server. Reconnecting in " + waitTime + " milliseconds.");
            }
        }
    }
}
