// The struct socket_fdinfo layout leasegate.core.peer.MacLibproc reads. On a Mac:
// clang -o /tmp/offsets socket_fdinfo_offsets.c && /tmp/offsets, then compare with MacLibproc.
// Measured on macOS 26 (arm64), offsets from the start of socket_fdinfo (792 bytes): soi_kind 256,
// insi_fport 264, insi_lport 268, insi_vflag 288, tcpsi_state 344; proc_fdinfo 8 (fd 0, fdtype 4).
// The ABS_ lines print exactly the numbers MacLibproc holds.
#include <stdio.h>
#include <stddef.h>
#include <libproc.h>
#include <sys/proc_info.h>
#define P(x) printf(#x " = %zu\n", (size_t)(x))
#define V(x) printf(#x " = %d\n", (int)(x))
#define ABS(m) printf("ABS_" #m " = %zu\n", offsetof(struct socket_fdinfo, m))
int main(void) {
    P(sizeof(struct proc_fdinfo));
    P(offsetof(struct proc_fdinfo, proc_fd));
    P(offsetof(struct proc_fdinfo, proc_fdtype));
    P(sizeof(struct socket_fdinfo));
    P(offsetof(struct socket_fdinfo, psi));
    P(offsetof(struct socket_info, soi_kind));
    P(offsetof(struct socket_info, soi_proto));
    P(offsetof(struct tcp_sockinfo, tcpsi_ini));
    P(offsetof(struct tcp_sockinfo, tcpsi_state));
    P(offsetof(struct in_sockinfo, insi_fport));
    P(offsetof(struct in_sockinfo, insi_lport));
    P(offsetof(struct in_sockinfo, insi_vflag));
    P(offsetof(struct in_sockinfo, insi_laddr));
    P(offsetof(struct in_sockinfo, insi_faddr));
    P(sizeof(struct in_addr_4in6));
    V(SOCKINFO_TCP); V(SOCKINFO_IN);
    V(PROX_FDTYPE_SOCKET); V(PROC_PIDLISTFDS); V(PROC_PIDFDSOCKETINFO);
    V(TSI_S_ESTABLISHED); V(INI_IPV4); V(INI_IPV6);
    V(PROC_ALL_PIDS);
    ABS(psi.soi_kind);
    ABS(psi.soi_proto.pri_tcp.tcpsi_ini.insi_fport);
    ABS(psi.soi_proto.pri_tcp.tcpsi_ini.insi_lport);
    ABS(psi.soi_proto.pri_tcp.tcpsi_ini.insi_vflag);
    ABS(psi.soi_proto.pri_tcp.tcpsi_state);
    return 0;
}
