%define __jar_repack %{nil}
%define debug_package %{nil}
%define __strip /bin/true
%define __os_install_post   /bin/true
%define __check_files /bin/true
Summary: nibblonian
Name: nibblonian
Version: 0.1.0
Release: 2
Epoch: 0
BuildArchitectures: noarch
Group: Applications
BuildRoot: %{_tmppath}/%{name}-%{version}-buildroot
License: BSD
Provides: nibblonian
Requires: iplant-service-config
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
install -d $RPM_BUILD_ROOT/usr/local/lib/nibblonian/
install -d $RPM_BUILD_ROOT/var/run/nibblonian/
install -d $RPM_BUILD_ROOT/var/lock/subsys/nibblonian/
install -d $RPM_BUILD_ROOT/var/log/nibblonian/
install -d $RPM_BUILD_ROOT/etc/nibblonian/

install nibblonian $RPM_BUILD_ROOT/etc/init.d/
install nibblonian-0.0.5-SNAPSHOT-standalone.jar $RPM_BUILD_ROOT/usr/local/lib/nibblonian/
install conf/log4j.properties $RPM_BUILD_ROOT/etc/nibblonian/

%post
/sbin/chkconfig --add nibblonian

%preun
if [ "$1" -eq "0" ] ; then
	/sbin/service nibblonian stop >/dev/null 2>&1
	/sbin/chkconfig --del nibblonian
fi

%postun
if [ "$1" -ge "1" ] ; then
	/sbin/service nibblonian condrestart >/dev/null 2>&1 || :
fi

%clean
lein clean
rm -r lib/*
rm -r $RPM_BUILD_ROOT

%files
%attr(-,iplant,iplant) /usr/local/lib/nibblonian/
%attr(-,iplant,iplant) /var/run/nibblonian/
%attr(-,iplant,iplant) /var/lock/subsys/nibblonian/
%attr(-,iplant,iplant) /var/log/nibblonian/
%attr(-,iplant,iplant) /etc/nibblonian/

%config %attr(0644,iplant,iplant) /etc/nibblonian/log4j.properties

%attr(0755,root,root) /etc/init.d/nibblonian
%attr(0644,iplant,iplant) /usr/local/lib/nibblonian/nibblonian-0.0.5-SNAPSHOT-standalone.jar


