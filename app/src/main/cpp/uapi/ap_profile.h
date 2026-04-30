/* SPDX-License-Identifier: GPL-2.0-or-later */
/*
 * APatch Full App Profile - userspace struct definition
 *
 * Matches the kernel-side app_profile layout for direct struct passing
 * through the supercall (syscall 45) interface.
 */

#ifndef _AP_UAPI_AP_PROFILE_H_
#define _AP_UAPI_AP_PROFILE_H_

#include <stdint.h>
#include <stdbool.h>

#define AP_MAX_PACKAGE_NAME 256
#define AP_MAX_GROUPS 32
#define AP_SELINUX_DOMAIN 64

#define AP_NAMESPACE_INHERITED   0
#define AP_NAMESPACE_GLOBAL      1
#define AP_NAMESPACE_INDIVIDUAL  2

struct ap_root_profile {
    int32_t uid;
    int32_t gid;

    uint32_t groups_count;
    int32_t groups[AP_MAX_GROUPS];

    struct {
        uint64_t effective;
        uint64_t permitted;
        uint64_t inheritable;
    } capabilities;

    char selinux_domain[AP_SELINUX_DOMAIN];

    int32_t namespaces;
};

struct ap_non_root_profile {
    bool umount_modules;
};

/* The full profile struct matching kernel's app_profile layout */
struct ap_profile {
    uint32_t version;
    char key[AP_MAX_PACKAGE_NAME];
    int32_t curr_uid;
    bool allow_su;

    union {
        struct {
            bool use_default;
            char template_name[AP_MAX_PACKAGE_NAME];
            struct ap_root_profile profile;
        } rp_config;

        struct {
            bool use_default;
            struct ap_non_root_profile profile;
        } nrp_config;
    };

    /* flags bitmap for partial updates via supercall:
     * bit 0: root profile fields changed
     * bit 1: non-root profile fields changed
     * bit 2: allow_su changed
     */
    uint32_t update_flags;
};

#endif /* _AP_UAPI_AP_PROFILE_H_ */
