#!/system/bin/sh

SCRIPTPATH=$0
DIR=$(cd ${SCRIPTPATH%/*}; pwd)

RICTYPE=0

if [ -e /system/bin/ric ]; then
	/system/bin/stop ric
	RICTYPE=1
fi

if [ -e /sbin/ric ]; then
	$DIR/busybox pkill /sbin/ric
	mount -o remount,rw /
	rm /sbin/ric
	mount -o remount,ro /
	$DIR/busybox pkill /sbin/ric
	RICTYPE=2
fi

mount -o remount,rw /system

if [ -e /system/bin/su ]; then
	rm /system/bin/su
fi
if [ -e /system/xbin/su ]; then
	rm /system/xbin/su
fi
dd if=$DIR/su of=/system/xbin/su
chown root.root /system/xbin/su
chmod 06755 /system/xbin/su
ln -s /system/xbin/su /system/bin/su

dd if=$DIR/Superuser.apk of=/system/app/Superuser.apk
chown root.root /system/app/Superuser.apk
chmod 0644 /system/app/Superuser.apk

dd if=$DIR/busybox of=/system/xbin/busybox
chown root.shell /system/xbin/busybox
chmod 0755 /system/xbin/busybox
/system/xbin/busybox --install -s /system/xbin

if [ $RICTYPE -gt 0 ]; then
	if [ ! -e /system/etc/init.d ]; then
		/system/bin/mkdir /system/etc/init.d
	fi
	/system/bin/chown root.root /system/etc/init.d
	/system/bin/chmod 0755 /system/etc/init.d
	if grep "/system/xbin/busybox run-parts /system/etc/init.d" /system/etc/hw_config.sh > /dev/null; then
		:
	else
		echo "/system/xbin/busybox run-parts /system/etc/init.d" >> /system/etc/hw_config.sh
	fi
fi

case $RICTYPE in
1)
	echo "#!/system/bin/sh" > /system/etc/init.d/00stop_ric
	echo "" >> /system/etc/init.d/00stop_ric
	echo "/system/bin/stop ric" >> /system/etc/init.d/00stop_ric
	chown root.root /system/etc/init.d/00stop_ric
	chmod 0755 /system/etc/init.d/00stop_ric
	;;
2)
	dd if=$DIR/00stop_ric of=/system/etc/init.d/00stop_ric
	chown root.root /system/etc/init.d/00stop_ric
	chmod 0755 /system/etc/init.d/00stop_ric
	;;
esac

mount -o remount,ro /system
