
How to create deb-package

1) From directory 'MyVpn' run <dh_make --native>
2) From folder 'Additional files' add to 'debian' 
	-control
	-postinst
	-postrm
	-preinst
	-prerm
	-rules
3) From 'debian' delete *.ex files, which you have added before
4) From 'MyVpn' run <fakeroot dh binary-arch>
5) *.deb file will be successfully created in ../
6) Bin file will have path "/usr/bin/MyVpn"

To install programm run <sudo dpkg -i nameofpackage.deb>

Programm will run in bg mode and will restart always

To show programm info run <systemctl -l status myvpn>

To disable deamon run <systemctl disable myvpn>

To stop programm run <systemctl stop myvpn>

Firewall file "/etc/firewall/"

Deamon file "/etc/systemd/system/myvpn.service"

For more information see off documentatin about systemd.

To remove programm run <sudo apt remove myvpn>

All additional files will be removed automatically

Have a nice day:)


P.S./////////////////////////////////////////////////////////////

	Be careful with /etc/firewall and ipmanager class

	Be afraid of ip conflicts

	NAT (10.0.0.0/8)

	Server - 10.0.0.1/8

	Range for clients - 10.0.0.2 - 100.0.0.255

	Programm use 1234 port and UDP protocol

     I
