Summary: nibblonian
Name: nibblonian
Version: 0.1.0
Release: 1
Epoch: 0
BuildArchitectures: noarch
Group: Applications
BuildRoot: %{_tmppath}/%{name}-%{version}-buildroot
License: BSD
Provides: nibblonian
Source0: %{name}-%{version}.tar.gz

%description
iPlant Nibblonian

%pre
getent group iplant > /dev/null || groupadd -r iplant
getent passwd iplant > /dev/null || useradd -r -g iplant -d /home/iplant -s /bin/bash -c "User for the iPlant services." iplant
exit 0

%prep
%setup -q
mkdir -p $RPM_BUILD_ROOT/etc/init.d/

%build
unset JAVA_OPTS
lein deps
lein uberjar

%install
install -d -o iplant -g iplant $RPM_BUILD_ROOT/usr/local/lib/nibblonian/
install -d -o iplant -g iplant $RPM_BUILD_ROOT/var/run/nibblonian/
install -d -o iplant -g iplant $RPM_BUILD_ROOT/var/lock/subsys/nibblonian/
install -d -o iplant -g iplant $RPM_BUILD_ROOT/var/log/nibblonian/
install -d -o iplant -g iplant $RPM_BUILD_ROOT/etc/nibblonian/

install -m755 -o iplant -g iplant nibblonian $RPM_BUILD_ROOT/etc/init.d/
install -m644 -o iplant -g iplant nibblonian-0.0.5-SNAPSHOT-standalone.jar $RPM_BUILD_ROOT/usr/local/lib/nibblonian/
install -m644 -o iplant -g iplant conf/log4j.properties $RPM_BUILD_ROOT/etc/nibblonian/
install -m644 -o iplant -g iplant conf/nibblonian.properties $RPM_BUILD_ROOT/etc/nibblonian/

%clean
lein clean
rm -r lib/*

%files
/usr/local/lib/nibblonian/
/var/run/nibblonian/
/var/lock/subsys/nibblonian/
/var/log/nibblonian/
/etc/nibblonian/

%config /etc/nibblonian/log4j.properties
%config /etc/nibblonian/nibblonian.properties

/etc/init.d/nibblonian
/usr/local/lib/nibblonian/nibblonian-0.0.5-SNAPSHOT-standalone.jar


