# FreeBSD JasperSoft Studio CE

This project contains tools to build the port files for
FreeBSD's /usr/ports/devel/jaspersoft-studio.

# Layout

* **config.sh** Project configuration
* **bin** Script files, expected to be invoked from top-level as `bin/script`
* **.baseline.xxx (generated)** pristine unpacked tree
* **maven-repo.${tag}** Maven repository for Eclipse build

# Prerequisite Ports

* devel/maven
* java/openjdk11

# Workflow

## Setup

1. `bin/apply-patches [directory ...]`

Unpacked distfiles + up-to-date patches => **xxx**

## Development

1. Work on **xxx**
1. `bin/build-studio [additional maven flags]` 

The challenge is to get a working build. Changes to
**xxx** should be committed and pushed to
the repo as required. At stable checkpoints, patches for the port should
be generated with:

1. `bin/generate-patches`

On a successful build, `xxx.tar.gz` is
generated. This can be unpacked to test the generated executable:

## java/eclipse port

When a usable executable has been generated the java/eclipse port can be
updated:

1. `bin/generate-patches`
1. Update the
[jaspersoft-studio-maven-repo](https://github.com/daemonblade/jaspersoft-studio-maven-repo)
project with the contents of **maven-repo.${TAG}**.
1. Update **devel-jaspersoft-studio** Makefile, distinfo, etc
1. Verify port build and installation
1. Submit port

# Notes

The porting-strategy is based on using the Linux port as the
base and converting it to FreeBSD. The pre-patch stage renames
Linux specific directories, and most of the work devolves to
changing the following lines of text:
* `linux.x86_64` => `freebsd.amd64`
