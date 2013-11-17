rem % gradeagent.bat version 1.0
rem % Run this batch file to grade the DIA assignment
rem % Thanks Ron, others:

rem % Compile the source:

javac *.java

% Run the programs:

start java NameServer
timeout 2

start java HostServer
start java HostServer 45051
timeout 2

% Open hostservers:

start firefox http://localhost:45050
start firefox http://localhost:45050

start firefox http://localhost:45051
start firefox http://localhost:45051

% Connect to the nameserver:

start firefox http://localhost:48050
