require eglibc.inc

DEFAULT_PREFERENCE = "-1"
DEPENDS += "gperf-native"
FILESPATHPKG =. "eglibc-svn:"
PV = "2.12"
PR = "${INC_PR}.8"
PR_append = "+svnr${SRCPV}"
SRCREV="12323"
EGLIBC_BRANCH="eglibc-2_12"
SRC_URI = "svn://svn.eglibc.org/branches;module=${EGLIBC_BRANCH};proto=svn \
           file://eglibc-svn-arm-lowlevellock-include-tls.patch \
           file://IO-acquire-lock-fix.patch \
           file://shorten-build-commands.patch \
           file://sh4_set_fpscr_2.12.patch \
           file://sh4_local-fpscr_values.patch \
           file://eglibc-dont-cache-slibdir.patch \
           file://armv4-eabi-compile-fix.patch \
           file://etc/ld.so.conf \
           file://generate-supported.mk"
S = "${WORKDIR}/${EGLIBC_BRANCH}/libc"
B = "${WORKDIR}/build-${TARGET_SYS}"

PACKAGES_DYNAMIC = "libc6*"
RPROVIDES_${PN}-dev = "libc6-dev virtual-libc-dev"
PROVIDES_${PN}-dbg = "glibc-dbg"

# the -isystem in bitbake.conf screws up eglibc sysroot
BUILD_CPPFLAGS = "-I${STAGING_INCDIR_NATIVE}"
TARGET_CPPFLAGS = "-I${STAGING_DIR_TARGET}${layout_includedir}"

GLIBC_ADDONS ?= "ports,nptl,libidn"

GLIBC_BROKEN_LOCALES = " _ER _ET so_ET yn_ER sid_ET tr_TR mn_MN gez_ET gez_ER bn_BD te_IN es_CR.ISO-8859-1"

#
# For now, we will skip building of a gcc package if it is a uclibc one
# and our build is not a uclibc one, and we skip a glibc one if our build
# is a uclibc build.
#
# See the note in gcc/gcc_3.4.0.oe
#

python __anonymous () {
    import bb, re
    uc_os = (re.match('.*uclibc$', bb.data.getVar('TARGET_OS', d, 1)) != None)
    if uc_os:
        raise bb.parse.SkipPackage("incompatible with target %s" %
                                   bb.data.getVar('TARGET_OS', d, 1))
}

EXTRA_OECONF = "--enable-kernel=${OLDEST_KERNEL} \
                --without-cvs --disable-profile --disable-debug --without-gd \
                --enable-clocale=gnu \
                --enable-add-ons=${GLIBC_ADDONS} \
                --with-headers=${STAGING_INCDIR} \
                --without-selinux \
                ${GLIBC_EXTRA_OECONF}"

do_configure () {
# override this function to avoid the autoconf/automake/aclocal/autoheader
# calls for now
# don't pass CPPFLAGS into configure, since it upsets the kernel-headers
# version check and doesn't really help with anything
        if [ -z "`which rpcgen`" ]; then
                echo "rpcgen not found.  Install glibc-devel."
                exit 1
        fi
        (cd ${S} && gnu-configize) || die "failure in running gnu-configize"
        find ${S} -name "configure" | xargs touch
        CPPFLAGS="" oe_runconf
}

rpcsvc = "bootparam_prot.x nlm_prot.x rstat.x \
	  yppasswd.x klm_prot.x rex.x sm_inter.x mount.x \
	  rusers.x spray.x nfs_prot.x rquota.x key_prot.x"

do_compile () {
	# -Wl,-rpath-link <staging>/lib in LDFLAGS can cause breakage if another glibc is in staging
	unset LDFLAGS
	oe_runmake libdir='${libdir}' slibdir='${base_libdir}' \
        localedir='${libdir}/locale'
	(
		cd ${S}/sunrpc/rpcsvc
		for r in ${rpcsvc}; do
			h=`echo $r|sed -e's,\.x$,.h,'`
			rpcgen -h $r -o $h || oewarn "unable to generate header for $r"
		done
	)
}

require eglibc-package.inc
