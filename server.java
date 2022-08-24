/*Student name: Yanhang Zhang
* Student number: 22299878
* Environment: Windows Subsystem for Linux (WSL)  &&  Ubuntu 20.04 LTS
*/

import java.util.ArrayList;
import java.util.*;
import java.io.*;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.text.*;
import java.nio.*;
import java.nio.channels.*;
import java.util.Iterator;

public class server {

    private ArrayList<Integer> neighbour; // storing stations neighbour's udp port
    // the following three variable is  control variables to check if all valid route have been reported
    private int route_number;
    private int fail_route;
    private int sum_route;
    private String final_time; // the final arriving time to the destination
    private int TCPport;
    private int UDPport;
    private ArrayList<String[]> timetable; // stations timetable
    private String station_name;
    private String modify_time; // the modify time of the timetable
    private int client_number; // the client-no will be used with client_socket hashmap
    private HashMap<Integer, SocketChannel> client_socket; // for storing the client socket to accept mutiple clients
    private ServerSocketChannel TCPserver;
    private DatagramChannel UDPserver;
    private Selector listen; // the selector to choose which socketchannel is ready to read or write

    public void run(String name, int tcpport, int udpport, ArrayList<Integer> neibour)
            throws IOException {
        final_time = "23:59";
        route_number = 0;
        fail_route = 0;
        modify_time = "23:59";
        neighbour = neibour;
        TCPport = tcpport;
        UDPport = udpport;
        station_name = name;
        client_number = 0;
        sum_route = 0;
        client_socket = new HashMap<Integer, SocketChannel>();
        
        // initiate neccessary variables
        timetable = read_timetable(station_name);
        modify_time = get_modify_time(station_name);

        TCPserver = ServerSocketChannel.open();
        TCPserver.configureBlocking(false);
        TCPserver.socket().bind(new InetSocketAddress(TCPport));

        UDPserver = DatagramChannel.open();
        UDPserver.configureBlocking(false);
        UDPserver.socket().bind(new InetSocketAddress(UDPport));

        listen = Selector.open();
        TCPserver.register(listen, SelectionKey.OP_ACCEPT);
        UDPserver.register(listen, SelectionKey.OP_READ);

        while (true) {
            System.out.println("--------get into the loop--------");

            listen.select();
            Set<SelectionKey> listen_keys = listen.selectedKeys();
            Iterator<SelectionKey> listen_iterator = listen_keys.iterator();

            // First listen the tcp server
            while (listen_iterator.hasNext()) {
                SelectionKey next = listen_iterator.next();
                listen_iterator.remove();
                // check which channel has been ready
                String channel_type = next.channel().getClass().getSimpleName();
                if (channel_type.equals("ServerSocketChannelImpl") || channel_type.equals("SocketChannelImpl")) {
                    tcpchannel_ready(next);
                }
                else if (channel_type.equals("DatagramChannelImpl")) {
                    udpchannel_ready(next);
                }
            }
        }
    }
    
    public void tcpchannel_ready(SelectionKey next)
            throws IOException {
        if (next.isAcceptable()) {
            // a new client is going to connect
            System.out.println("-------Get a connection----------\n");
            SocketChannel accept = TCPserver.accept();
            accept.configureBlocking(false);
            accept.register(listen, SelectionKey.OP_READ);
        } else if (next.isReadable()) {
            // get a request from a client
            SocketChannel client = (SocketChannel) next.channel();
            ByteBuffer buffer = ByteBuffer.allocate(1024);
            int read = client.read(buffer);
            String rawdata = new String(buffer.array(), 0, read);
            String destination = handler_request(rawdata);
            //check if the request is favicon
            if (is_favicon(destination)) {
                client.close();
                return;
            }
            //check if timetable has been updated
            if (is_timetableChang(station_name, modify_time)) {
                timetable = read_timetable(station_name);
            }
            System.out.println("---------1------------");
            boolean exist = false;
            /* first checking timetable
            *  if the origin station is nearby the destination, immedately send information back
            *  otherwise send request to other stations
            */
            for (int i = 1; i < timetable.size(); i++) {
                if (timetable.get(i)[4].equals(destination) && timetable.get(i)[0].compareTo(get_current_time()) > 0) {
                    System.out.println(get_current_time());
                    String[] next_bus = Find_nextBus(i);
                    String responseHeaderLines = "HTTP/1.1 200 OK\r\n";
                    responseHeaderLines += "\r\n";
                    String responseBody = "leaving time is:  " + next_bus[0] + "\n" + "bus number is:  " + next_bus[1]
                            + "\n" + "stop name is:  " + next_bus[2] + "\n" + "next stop is: " + next_bus[3] + "\n"
                            + "arriving time is:  " + next_bus[4];
                    String response = responseHeaderLines + responseBody;
                    client.register(listen, SelectionKey.OP_WRITE);
                    SelectionKey key = client.keyFor(listen);
                    key.attach(response);
                    System.out.println("-----------find the destination immediately---------");
                    break;
                } else if (timetable.get(i)[0].compareTo(get_current_time()) < 0 && i == timetable.size() - 1) {
                    String responseHeaderLines = "HTTP/1.1 404 Not Found\r\n";
                    responseHeaderLines += "\r\n";
                    String responseBody = "no more bus today\nPlease find another way";
                    String response = responseHeaderLines + responseBody;
                    client.register(listen, SelectionKey.OP_WRITE);
                    SelectionKey key = client.keyFor(listen);
                    key.attach(response);
                    System.out.println("-------------no bus today from origin to destination---------");
                    break;
                } else if (timetable.get(i)[4].equals(destination)) {
                    exist = true;
                    continue;
                } else if (exist == false && timetable.get(i)[0].compareTo(get_current_time()) > 0
                        && i == (timetable.size() - 1)) {
                    client_number++;
                    client_socket.put(client_number, client);
                    String data = String.valueOf(client_number) + ";" + "Find;" + destination + ";" + station_name + ";"
                            + String.valueOf(UDPport);
                    for (int j = 0; j < neighbour.size(); j++) {
                        UDPserver.send(ByteBuffer.wrap(data.getBytes()), new InetSocketAddress("127.0.0.1", neighbour.get(j)));
                    }
                    break;
                } else {
                    continue;
                }
            }

        } else if (next.isWritable()) { //send data to the client
            System.out.println("-------get into the step to send back data---------");
            SocketChannel client = (SocketChannel) next.channel();
            String data = (String) next.attachment();
            System.out.println(data);
            client.write(ByteBuffer.wrap(data.getBytes()));
            client.close();
        }
    }

    public void udpchannel_ready(SelectionKey next) throws IOException {
        if (next.isReadable()) {
            // deal with the rawdata from udp channel
            DatagramChannel udp_channel = (DatagramChannel) next.channel();
            ByteBuffer recv_buffer = ByteBuffer.allocate(1024);
            recv_buffer.clear();
            SocketAddress back_port = udp_channel.receive(recv_buffer);
            recv_buffer.flip();
            byte[] mid_transfer = new byte[recv_buffer.limit()];
            recv_buffer.get(mid_transfer);
            String recv_data = new String(mid_transfer);
            String[] data_list = recv_data.split(";");
            String head = data_list[1];
            if (head.equals("Find")) {
                // find the route from origin to destination
                for (int i = 1; i < timetable.size(); i++) {
                    // finde the destination
                    if (data_list[2].equals(timetable.get(i)[4])) {
                        data_list[1] = "Route";
                        String back_data = String.join(";", data_list);
                        String backdata = back_data + ";" + station_name + ";" + String.valueOf(UDPport);
                        udp_channel.send(ByteBuffer.wrap(backdata.getBytes()), back_port);
                        break;
                    }
                    // do not find the destination and send request to other stations
                    else if (i == timetable.size() - 1 && (!timetable.get(i)[4].equals(data_list[2])
                            || timetable.get(i)[0].compareTo(get_current_time()) > 0)) {
                        String send_data = recv_data + ";" + station_name + ";" + String.valueOf(UDPport);
                        for (int j = 0; j < neighbour.size(); j++) {
                            if (!is_in_array(String.valueOf(neighbour.get(j)), data_list)) {
                                udp_channel.send(ByteBuffer.wrap(send_data.getBytes()),
                                        new InetSocketAddress("127.0.0.1", neighbour.get(j)));
                            } else {
                                continue;
                            }
                        }
                        break;
                    } else {
                        continue;
                    }
                }
            } else if (head.equals("Route")) {
                // pass the route back to the origin
                // if this station is not the origin
                sum_route++;
                if (!station_name.equals(data_list[3])) {
                    for (int i = 0; i < data_list.length; i++) {
                        if (station_name.equals(data_list[i])) {
                            int send_port = Integer.valueOf(data_list[i - 1]);
                            String send = String.join(";", data_list);
                            udp_channel.send(ByteBuffer.wrap(send.getBytes()),
                                    new InetSocketAddress("127.0.0.1", send_port));
                            break;
                        } else {
                            continue;
                        }
                    }
                } else { // start to find the final time to arrive the destination
                    data_list[1] = "Reroute";
                    final String current_time = get_current_time();
                    for (int i = 1; i < timetable.size(); i++) {
                        if (timetable.get(i)[0].compareTo(current_time) > 0
                                && timetable.get(i)[4].equals(data_list[5])) {
                            route_number++;
                            String send_data = String.join(";", data_list);
                            send_data = send_data + ";" + timetable.get(i)[3];
                            udp_channel.send(ByteBuffer.wrap(send_data.getBytes()),
                                    new InetSocketAddress("127.0.0.1", Integer.valueOf(data_list[6])));
                            break;
                        } else if (i == timetable.size() - 1 && (!timetable.get(i)[4].equals(data_list[5])
                                || timetable.get(i)[0].compareTo(get_current_time()) < 0)) {
                            fail_route++;
                            System.out.printf("the fail route is: %d", fail_route);
                            System.out.printf("the sum_route is: %d", sum_route);
                            if (fail_route == sum_route) {
                                String responseHeaderLines = "HTTP/1.1 404 Not Found\r\n";
                                responseHeaderLines += "\r\n";
                                String responseBody = "no bus/train today to destination\nplease find another way";
                                String response = responseHeaderLines + responseBody;
                                SocketChannel client = client_socket.get(Integer.valueOf(data_list[0]));
                                client.register(listen, SelectionKey.OP_WRITE);
                                SelectionKey key = client.keyFor(listen);
                                key.attach(response);
                                client_socket.remove(Integer.valueOf(data_list[0]));
                                break;
                            }
                        } else {
                            continue;
                        }
                    }
                }
            } else if (head.equals("Reroute")) {
                // find the final time to the destination
                int send_port = 0;
                String ctime = data_list[data_list.length - 1];
                //if this station is the last station in this trip before arriving destination
                if (station_name.equals(data_list[data_list.length - 3])) {
                    data_list[1] = "Get";
                    for (int i = 0; i < data_list.length - 1; i++) {
                        if (station_name.equals(data_list[i])) {
                            send_port = Integer.valueOf(data_list[i - 1]);
                            break;
                        }
                    }
                    for (int i = 1; i < timetable.size(); i++) {
                        if (timetable.get(i)[0].compareTo(ctime) > 0 && timetable.get(i)[4].equals(data_list[2])) {
                            String arrive_time = timetable.get(i)[3];
                            data_list[data_list.length - 1] = arrive_time;
                            String send = String.join(";", data_list);
                            udp_channel.send(ByteBuffer.wrap(send.getBytes()),
                                    new InetSocketAddress("127.0.0.1", send_port));
                            break;
                        } else if (i == timetable.size() - 1 && (!timetable.get(i)[4].equals(data_list[2])
                                || timetable.get(i)[0].compareTo(ctime) < 0)) {
                            String msg = "no";
                            data_list[data_list.length - 1] = msg;
                            String send = String.join(";", data_list);
                            udp_channel.send(ByteBuffer.wrap(send.getBytes()), 
                                    new InetSocketAddress("127.0.0.1", send_port));
                            System.out.println("-----------has sent the data to python---------------");
                            break;
                        } else {
                            continue;
                        }
                    }
                } else { // if this is not the last station
                    String next_station = "";
                    for (int i = 0; i < data_list.length - 1; i++) {
                        if (data_list[i].equals(station_name)) {
                            send_port = Integer.valueOf(data_list[i + 3]);
                            next_station = data_list[i + 2];
                        }
                    }
                    for (int i = 1; i < timetable.size(); i++) {
                        if (timetable.get(i)[0].compareTo(ctime) > 0 && timetable.get(i)[4].equals(next_station)) {
                            ctime = timetable.get(i)[3];
                            data_list[data_list.length - 1] = ctime;
                            String send = String.join(";", data_list);
                            udp_channel.send(ByteBuffer.wrap(send.getBytes()),
                                    new InetSocketAddress("127.0.0.1", send_port));
                            break;
                        } else if (i == timetable.size() - 1 && (!timetable.get(i)[4].equals(data_list[2])
                                || timetable.get(i)[0].compareTo(ctime) < 0)) {
                            data_list[1] = "Get";
                            String msg = "no";
                            data_list[data_list.length - 1] = msg;
                            String send = String.join(";", data_list);
                            udp_channel.send(ByteBuffer.wrap(send.getBytes()),
                                    new InetSocketAddress("127.0.0.1", send_port));
                            break;
                        } else {
                            continue;
                        }
                    }
                }
            } else if (head.equals("Get")) {
                // pass the final time back to the destination
                route_number--;
                int send_port = 0;
                // if the station is not the origin one
                if (!data_list[3].equals(station_name)) {
                    for (int i = 0; i < data_list.length - 1; i++) {
                        if (data_list[i].equals(station_name)) {
                            send_port = Integer.valueOf(data_list[i - 1]);
                            String send = String.join(";", data_list);
                            udp_channel.send(ByteBuffer.wrap(send.getBytes()),
                                    new InetSocketAddress("127.0.0.1", send_port));
                        }
                    }
                }
                // the arriving has been sent to the origin
                else {
                    System.out.printf("--------------route number is: %d", route_number);
                    if (is_time(data_list[data_list.length - 1])) {
                        String arrive_time = data_list[data_list.length - 1];
                        if (arrive_time.compareTo(final_time) < 0) {
                            final_time = arrive_time;
                        }
                    }
                    //if all possible route have been reported to the origin station
                    if (route_number == 0) {
                        if (final_time.compareTo("23:59") < 0) {
                            String[] next_bus = new String[5];
                            for (int i = 1; i < timetable.size(); i++) {
                                if (timetable.get(i)[0].compareTo(get_current_time()) > 0
                                        && timetable.get(i)[4].equals(data_list[5])) {
                                    next_bus = Find_nextBus(i);
                                    break;
                                }
                            }
                            String responseHeaderLines = "HTTP/1.1 200 OK\r\n";
                            responseHeaderLines += "\r\n";
                            String responseBody = "leaving time is:  " + next_bus[0] + "\n" + "bus number is:  "
                                    + next_bus[1] + "\n" + "stop name is:  " + next_bus[2] + "\n" + "next station is: "
                                    + next_bus[3] + "\n" + "arriving at next station at: " + next_bus[4] + "\n"
                                    + "arriving destination at:  " + final_time;
                            String response = responseHeaderLines + responseBody;
                            SocketChannel client = client_socket.get(Integer.valueOf(data_list[0]));
                            client.register(listen, SelectionKey.OP_WRITE);
                            SelectionKey key = client.keyFor(listen);
                            key.attach(response);
                            client_socket.remove(Integer.valueOf(data_list[0]));
                        } else {
                            String responseHeaderLines = "HTTP/1.1 404 Not Found\r\n";
                            responseHeaderLines += "\r\n";
                            System.out.println(recv_data);
                            String responseBody = "no bus/train today to destination\nplease find another way";
                            String response = responseHeaderLines + responseBody;
                            SocketChannel client = client_socket.get(Integer.valueOf(data_list[0]));
                            client.register(listen, SelectionKey.OP_WRITE);
                            SelectionKey key = client.keyFor(listen);
                            key.attach(response);
                            client_socket.remove(Integer.valueOf(data_list[0]));
                        }
                    }
                }
            }
        }
    }

    public String get_current_time() {
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm");
        sdf.setTimeZone(TimeZone.getTimeZone("Asia/Shanghai"));
        String ctime = sdf.format(Calendar.getInstance().getTime());
        return ctime;
    }

    public String get_modify_time(String stationname) {
        String file_name = "tt-" + stationname;
        File file = new File(file_name);
        long m_time = file.lastModified();
        Format date_format = new SimpleDateFormat("hh:mm");
        String date_string = date_format.format(m_time);
        return date_string;
    }

    public static ArrayList<String[]> read_timetable(String stationname) {
        ArrayList<String[]> timetable = new ArrayList<String[]>();
        String station_name = "tt-" + stationname;
        String delimeter = ",";
        try {
            File file = new File(station_name);
            InputStream is = new FileInputStream(file);
            String line;
            BufferedReader reader = new BufferedReader(new InputStreamReader(is));
            line = reader.readLine();
            while (line != null) {
                String[] temp;
                temp = line.split(delimeter);
                timetable.add(temp);
                line = reader.readLine();
            }
            reader.close();
            is.close();
        } catch (Exception e) {
            System.out.println("there is an error during reading the file");
            e.printStackTrace();
        }
        return timetable;
    }

    public static boolean is_favicon(String destination) {
        if (destination.equals("favicon.ico")) {
            return true;
        } else {
            return false;
        }
    }

    public boolean is_in_array(String a,String[] b) {
        boolean in = false;
        for (String s : b) {
            if (s.equals(a)) {
                in = true;
            }
        }
        return in;
    }

    public String[] Find_nextBus(final int i) {
        String[] next_bus = new String[5];
        next_bus[0] = timetable.get(i)[0];
        next_bus[1] = timetable.get(i)[1];
        next_bus[2] = timetable.get(i)[2];
        next_bus[3] = timetable.get(i)[3];
        next_bus[4] = timetable.get(i)[4];
        return next_bus;
    }

    public static boolean is_time(final String data) {
        if (data.contains(":")) {
            return true;
        } else {
            return false;
        }
    }

    public static String handler_request(final String rawdata) {
        String destination = "";
        String[] request_lines = rawdata.split("\n");
        String[] request_header = request_lines[0].split("/|\\?|=| ");
        for (int i = 0; i < request_header.length; i++) {
            if (request_header[i].equals("HTTP")) {
                destination = request_header[i - 1];
            } else {
                continue;
            }
        }
        return destination;
    }

    public static boolean is_timetableChang(final String stationname, final String c_time) {
        File station_file = new File("tt-" + stationname);
        Format date_format = new SimpleDateFormat("hh:mm");
        long m_time = station_file.lastModified();
        String dateString = date_format.format(m_time);
        if (dateString.compareTo(c_time) > 0) {
            return true;
        } else {
            return false;
        }
    }

    public static void reset_variable(String final_time, int route_number, int fail_route) {
        final_time = "23:59";
        route_number = 0;
        fail_route = 0;
    }

    public static void main(final String[] args) throws IOException {
        if (args.length < 4) {
            System.out.println("please enter enough parameters");
            System.exit(0);
        }
        ArrayList<Integer> neibour = new ArrayList<Integer>();
        int length = args.length;
        System.out.println(length);
        String station = args[0];
        int tcpport = Integer.valueOf(args[1]);
        int udpport = Integer.valueOf(args[2]);
        for (int i = 3; i < length; i++) {
            int port_number = Integer.valueOf(args[i]);
            neibour.add(port_number);
        }

        server serverstation = new server();
        serverstation.run(station, tcpport, udpport, neibour);
        
    }
}