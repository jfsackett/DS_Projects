@echo on
rem This is shim.bat
rem Change this to your development directory:
cd \Users\Joe\git\DS_Projects\Mimer
echo "We are now in a shim called from the Web Browser 3"
echo Arg one is: %1
rem Change this to point to your Handler directory:
rem cd \Users\Joe\git\DS_Projects\Mimer\bin
rem pause
rem have to set classpath in batch, passing as arg does not work.
rem Change this to point to your own Xstream library files:
set clspath=C:\Users\Joe\git\DS_Projects\Mimer\bin;C:\Users\Joe\git\DS_Projects\Mimer\xstream-1.4.5.jar;C:\Users\Joe\git\DS_Projects\Mimer\xpp3_min-1.1.4c.jar;C:\Users\Joe\git\DS_Projects\Mimer\xmlpull-1.1.3.1.jar
rem pass the name of the first argument to java:
java -cp %clspath% -Dfirstarg=%1 BCHandler
rem pause