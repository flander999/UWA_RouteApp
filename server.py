#!/usr/bin/env python3
#-*- coding:utf-8 -*-

'''
Student name: Yanhang Zhang
Student number:   22299878

Environment: Windows Subsystem for Linux (WSL)  &&  Ubuntu 20.04 LTS
'''

import socket
import select
import time
import sys
import re
import sys
import os

def reset_variable(final_time,route_number,fail_route):
    final_time = "23:59"
    route_number = 0
    fail_route = 0
    sum_route = 0

def avoid_favicon(sk):
    responseHeaderLines = "HTTP/1.1 404 Not Found\r\n"
    responseHeaderLines += "\r\n"
    responseBody = ""
    response = responseHeaderLines + responseBody
    sk.send(response.encode('utf-8'))
    sk.close()
    listen_list.remove(sk)
    

def is_favicon(destination):
    if destination == 'favicon.ico':
        return True
    else:
        return False

def Find_nextBus(a, timetable):
    leaving_time = timetable[a][0]
    bus_no = timetable[a][1]
    stop_no = timetable[a][2]
    arriving_time = timetable[a][3]
    next_station = timetable[a][4]
    return leaving_time,bus_no,stop_no,arriving_time,next_station

def is_time(data):
    if ':' in data:
        return True
    else:
        return False

def Send_response(responseHeader,body,sk):
    response = responseHeader + body
    sk.send(response.encode('utf-8'))
    sk.close()


def Handler_request(rawdata):
    destination = ""
    reuquest_header_lines = rawdata.splitlines()
    http_header_data = reuquest_header_lines[0]
    data_list = re.split(r"/|\?\s|=| ",http_header_data)
    for i in range(len(data_list)):
        if data_list[i] == 'HTTP':
            destination = data_list[i-1]
        else:
            continue
    return destination



def is_timetableChange(stationname,c_time):
    change_time = time.ctime(os.stat('tt-'+stationname).st_mtime)
    if c_time < change_time:
        return True
    else:
        return False


def readTimetable(stationname):
    time_table = []
    with open('tt-'+stationname,'r') as f:
        for line in f.readlines():
            line = line.strip('\n')
            lines = line.split(',')
            time_table.append(lines)
    f.close()
    return time_table


def main():
    #read the input from command
    if len(sys.argv) < 5:
        print("Please insert engough parameters")
        sys.exit()
    
    station_name = sys.argv[1]
    TCPport = int(sys.argv[2])
    UDPport = int(sys.argv[3])
    neighbour = []
    for i in range(4, len(sys.argv)):
        number = int(sys.argv[i])
        neighbour.append(number)
    
    #the final arriving time to the destination
    final_time = "23:59"
    host = '127.0.0.1'

    #based on the station name to read the txt file as a list
    timetable = readTimetable(station_name)
    modify_time = time.ctime(os.stat('tt-'+station_name).st_mtime)

    #create a TCP socket to listen request from browswer
    TCPsocket = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    TCPsocket.bind((host,TCPport))
    TCPsocket.listen(10)
    TCPsocket.setblocking(False)

    #innitial a UDP socket for i/o of this server station
    UDPsocket = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    UDPsocket.bind((host, UDPport))

    #the list to choose which socketchannel is ready to read or write
    listen_list = [TCPsocket,UDPsocket,]

    #the following three variable is  control variables to check if all valid route have been reported
    route_number = 0
    fail_route = 0
    sum_route = 0

    #the client-no will be used with client_socket dictionary
    client_number = 0
    client_socket = {}

    #set up an infinite loop to listen and deal with the udp things
    #And listen for all different socket by using select module
    while True:
        print('-------------------get into the loop---------------')
        read,write,error = select.select(listen_list,[],[])

        for sk in read:
            #there is a new client get into connection
            if sk == TCPsocket:
                print('-----------a new TCP connection-------------')
                conn, addr = TCPsocket.accept()
                conn.setblocking(False)
                listen_list.append(conn)
            
            #the udp socket recieve datagram from others
            elif sk == UDPsocket:
                print('-----------a conmmunication from UDP--------------')
                data, address= UDPsocket.recvfrom(1024)
                data1 = data.decode('utf-8')
                print(data1)
                recv_data = data1.split(';')
                if recv_data[1] == 'Find':
                    exist = False
                    for i in range(1, len(timetable)):
                        #find the destination
                        if recv_data[2] == timetable[i][4] :
                            recv_data[1] = 'Route'
                            data2 = ";".join(recv_data)
                            back_data = data2  + ';' + station_name + ';' + str(UDPport)
                            sk.sendto(back_data.encode('utf-8'), address)
                            break
                        #send the route of str to next station
                        elif i == len(timetable)-1 and (timetable[i][4] != recv_data[2] or timetable[i][0] < ctime ) :
                            send_data = data1 + ';' + station_name + ';' + str(UDPport)
                            station_num = (len(recv_data)-1)/2
                            for i in range(len(neighbour)):
                                if str(neighbour[i]) not in recv_data:
                                    sk.sendto(send_data.encode('utf-8'),(host,neighbour[i]))
                                else:
                                    continue
                        else:
                            continue
                #send back the entire route from origin to destination
                elif recv_data[1] == 'Route':
                    sum_route += 1
                    #if this station is not the origin, continue to send back
                    if station_name != recv_data[3]:
                        for i in range(len(recv_data)):
                            if recv_data[i] == station_name:
                                send_port = int(recv_data[i-1])
                                sk.sendto(data,(host,send_port))
                                break
                            else:
                                continue
                    #if this is the origin
                    else:
                        recv_data[1] = 'Reroute'
                        now = time.strftime("%H:%M")
                        for i in range(1, len(timetable)):
                            if timetable[i][0]> now and timetable[i][4] == recv_data[5]:
                                route_number += 1
                                recv_data.append(timetable[i][3])
                                data2 = ";".join(recv_data)
                                sk.sendto(data2.encode('utf-8'),(host, int(recv_data[6])))
                                break
                            elif i == len(timetable)-1 and (timetable[i][4] != recv_data[5] or timetable[i][0] < ctime ):
                                fail_route += 1
                                if fail_route == sum_route:
                                    responseHeaderLines = "HTTP/1.1 404 Not Found\r\n"
                                    responseHeaderLines += "\r\n"
                                    responseBody = "no bus/train today to destination\nplease find another way1"
                                    send_sk = client_socket[recv_data[0]]
                                    Send_response(responseHeaderLines,responseBody,send_sk)
                                    listen_list.remove(send_sk)
                                    reset_variable(final_time,route_number,fail_route)
                            else:
                                continue
                #fllowing by route to find the arriving time
                elif recv_data[1] == 'Reroute':
                    send_port = 0
                    ctime = recv_data[len(recv_data)-1]
                    if recv_data[len(recv_data)-3] == station_name:
                        recv_data[1] = 'Get'
                        for i in range(len(recv_data)):
                            if recv_data[i] == station_name:
                                send_port = int(recv_data[i-1])
                                break
                        for i in range(1, len(timetable)):
                            if timetable[i][0]> ctime and timetable[i][4] == recv_data[2]:
                                arrive_time = timetable[i][3]
                                recv_data[len(recv_data)-1] = arrive_time
                                data2 = ";".join(recv_data)
                                sk.sendto(data2.encode('utf-8'),(host,send_port))
                                break
                            elif i == len(timetable)-1 and (timetable[i][4] != recv_data[2] or timetable[i][0] < ctime):
                                msg = "no"
                                recv_data[len(recv_data)-1] = msg
                                data2 = ";".join(recv_data)
                                sk.sendto(data2.encode('utf-8'),(host,send_port))
                                break
                            else:
                                continue
                    else:
                        next_station = ""
                        send_port = 0
                        for i in range(len(recv_data)):
                            if recv_data[i] == station_name:
                                send_port = int(recv_data[i+3])
                                next_station = recv_data[i+2]
                        for i in range(1,len(timetable)):
                            if timetable[i][0]> ctime and timetable[i][4] == next_station:
                                ctime = timetable[i][3]
                                recv_data[len(recv_data)-1] = ctime
                                data2 = ";".join(recv_data)
                                sk.sendto(data2.encode('utf-8'),(host,send_port))
                                break
                            elif i == len(timetable)-1 and (timetable[i][4] != next_station or timetable[i][0] < ctime):
                                recv_data[0] = 'Get'
                                msg = "no"
                                recv_data[len(recv_data)-1] = msg
                                data2 = ";".join(recv_data)
                                sk.sendto(data2.encode('utf-8'),(address))
                                break
                            else:
                                continue
                #the arrive time has been sent
                elif recv_data[1] == 'Get':
                    route_number -= 1
                    #if the station is not the origin one
                    if station_name != recv_data[3]:
                        for i in range(len(recv_data)):
                            if recv_data[i] == station_name:
                                send_port = int(recv_data[i-1])
                                sk.sendto(data,(host,send_port))
                    #the arriving time has been sent to the origin
                    else:
                        print('-----------------route number is: %d',route_number)
                        if is_time(recv_data[len(recv_data)-1]):
                            arrive_time = recv_data[len(recv_data)-1]
                            if arrive_time < final_time:
                                final_time = arrive_time
                            else:
                                continue

                        if route_number == 0:
                            if final_time != "23:59":
                                stop = ""
                                leaving_time = ""
                                bus_no = ""
                                next_station = ""
                                for i in range(1,len(timetable)):
                                    if timetable[i][0]> time.strftime("%H:%M") and timetable[i][4] == recv_data[5]:
                                        leaving_time,bus_no,stop_no,Atime,next_station = Find_nextBus(i,timetable)
                                        break
                                responseHeaderLines = "HTTP/1.1 200 OK\r\n"
                                responseHeaderLines += "\r\n"
                                responseBody = "leaving time is:  " + leaving_time + "\n" + "bus number is:  " + bus_no + "\n" + "stop name is:  " + stop_no + "\n" + "next station is: "+ next_station + "\n" + "arriving at next station at: " + Atime + "\n" + "arriving destination at:  " + final_time
                                send_sk = client_socket[recv_data[0]]
                                Send_response(responseHeaderLines,responseBody,send_sk)
                                listen_list.remove(send_sk)
                                reset_variable(final_time,route_number,fail_route)
                                break
                            else:
                                responseHeaderLines = "HTTP/1.1 404 Not Found\r\n"
                                responseHeaderLines += "\r\n"
                                print(recv_data)
                                responseBody = "no bus/train today to destination\nplease find another way"
                                send_sk = client_socket[recv_data[0]]
                                Send_response(responseHeaderLines,responseBody,send_sk)
                                listen_list.remove(send_sk)
                                reset_variable(final_time,route_number,fail_route)
                                break
            else:
                print('----------------request from client----------------')
                data = sk.recv(1024).decode('utf-8')
                if data == '':
                    listen_list.remove(sk)
                    sk.close()
                    break
                else:
                    exist = False
                    destination = Handler_request(data)
                    print("the request is to find: " + destination)
                    if is_favicon(destination):
                        avoid_favicon(sk)
                        listen_list.remove(sk)
                        break
                    if is_timetableChange(station_name,modify_time):
                        timetable =  readTimetable(station_name)
                        modify_time = time.ctime(os.stat('tt-'+destination).st_mtime)
                    for i in range(1,len(timetable)):
                        if (timetable[i][4] == destination) and timetable[i][0] > time.strftime("%H:%M"):
                            leaving_time,bus_no,stop_no,final_time,arrive_stop = Find_nextBus(i,timetable)
                            responseHeaderLines = "HTTP/1.1 200 OK\r\n"
                            responseHeaderLines += "\r\n"
                            responseBody = "leaving time is:  " + leaving_time + "\n" + "bus number is:  " + bus_no + "\n" + "stop name is:  " + stop_no + "\n" + "next stop is: "+ arrive_stop + "\n" + "arriving time is:  " + final_time
                            Send_response(responseHeaderLines,responseBody,sk)
                            listen_list.remove(sk)
                            break
                        elif timetable[i][0] <= time.strftime("%H:%M") and i == len(timetable)-1:
                            responseHeaderLines = "HTTP/1.1 404 Not Found\r\n"
                            responseHeaderLines += "\r\n"
                            responseBody = "no more bus today\nPlease find another way"
                            response = responseHeaderLines + responseBody
                            Send_response(responseHeaderLines,responseBody,sk)
                            listen_list.remove(sk)
                            break
                        elif timetable[i][4] == destination:
                            exist = True
                            continue
                        elif exist == False and timetable[i][0] > time.strftime("%H:%M") and i == len(timetable)-1:
                            client_number += 1
                            client_socket[str(client_number)] = sk
                            send_data = str(client_number) + ";" + 'Find;'+destination+";"+station_name+";"+str(UDPport)
                            for i in neighbour:
                                UDPsocket.sendto(send_data.encode('utf-8'),(host,i))
                        else:
                            continue
    UDPsocket.close()
    TCPsocket.close()

if __name__ == '__main__':
    main()