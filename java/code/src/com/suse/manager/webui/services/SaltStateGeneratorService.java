/**
 * Copyright (c) 2016--2020 SUSE LLC
 *
 * This software is licensed to you under the GNU General Public License,
 * version 2 (GPLv2). There is NO WARRANTY for this software, express or
 * implied, including the implied warranties of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. You should have received a copy of GPLv2
 * along with this software; if not, see
 * http://www.gnu.org/licenses/old-licenses/gpl-2.0.txt.
 *
 * Red Hat trademarks are not licensed under GPLv2. No permission is
 * granted to use or replicate Red Hat trademarks that are incorporated
 * in this software or its documentation.
 */
package com.suse.manager.webui.services;

import static com.suse.manager.webui.services.SaltConstants.PILLAR_DATA_FILE_EXT;
import static com.suse.manager.webui.services.SaltConstants.PILLAR_IMAGE_DATA_FILE_EXT;
import static com.suse.manager.webui.services.SaltConstants.PILLAR_IMAGE_DATA_FILE_PREFIX;
import static com.suse.manager.webui.services.SaltConstants.SALT_CONFIG_STATES_DIR;
import static com.suse.manager.webui.services.SaltConstants.SALT_SERVER_STATE_FILE_PREFIX;
import static com.suse.manager.webui.services.SaltConstants.SUMA_PILLAR_IMAGES_DATA_PATH;
import static com.suse.manager.webui.services.SaltConstants.SUMA_STATE_FILES_ROOT_PATH;
import static com.suse.manager.webui.utils.SaltFileUtils.defaultExtension;

import com.redhat.rhn.common.conf.ConfigDefaults;
import com.redhat.rhn.common.util.FileUtils;
import com.redhat.rhn.domain.config.ConfigChannel;
import com.redhat.rhn.domain.org.Org;
import com.redhat.rhn.domain.server.MinionServer;
import com.redhat.rhn.domain.server.MinionServerFactory;
import com.redhat.rhn.domain.server.ServerGroup;
import com.redhat.rhn.domain.state.OrgStateRevision;
import com.redhat.rhn.domain.state.ServerGroupStateRevision;
import com.redhat.rhn.domain.state.ServerStateRevision;
import com.redhat.rhn.domain.state.StateFactory;
import com.redhat.rhn.domain.state.StateRevision;
import com.redhat.rhn.domain.user.User;
import com.suse.manager.webui.controllers.StatesAPI;
import com.suse.manager.webui.services.pillar.MinionPillarManager;
import com.suse.manager.webui.utils.SaltConfigChannelState;
import com.suse.manager.webui.utils.SaltPillar;
import com.suse.manager.webui.utils.SaltStateGenerator;
import com.suse.manager.webui.utils.salt.custom.OSImageInspectSlsResult;

import org.apache.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Service to manage the Salt states generated by Suse Manager.
 */
public enum SaltStateGeneratorService {

    // Singleton instance of this class
    INSTANCE;

    /** Logger */
    private static final Logger LOG = Logger.getLogger(SaltStateGeneratorService.class);

    private Path suseManagerStatesFilesRoot;

    SaltStateGeneratorService() {
        suseManagerStatesFilesRoot = Paths.get(SUMA_STATE_FILES_ROOT_PATH);
    }

    /**
     * Generate OS Image specific pillar used in terminals
     * @param image the OS image resulting image from an inspection
     * @param bundle the OS image bundle resulting image from an inspection
     * @param bootImage the OS image boot image resulting image from an inspection
     * @param urlBase the OS Image store URL
     */
    public void generateOSImagePillar(OSImageInspectSlsResult.Image image, OSImageInspectSlsResult.Bundle bundle,
                                      OSImageInspectSlsResult.BootImage bootImage, String urlBase) {
        try {
            SaltPillar pillar = new SaltPillar();
            String name = image.getName();
            String version = image.getVersion();
            String bootImageName = name + "-" + version;
            String localPath = "image/" + bundle.getBasename() + "-" + bundle.getId();
            String bootLocalPath = bundle.getBasename() + "-" + bundle.getId();

            Map<String, Object> bootImagePillar = generateBootImagePillar(bootImage, bootImageName,
                                                                          localPath, bootLocalPath);

            Map<String, Object> imagePillar = generateImagePillar(image, bundle, bootImage, urlBase, name, version,
                    localPath);
            pillar.add("boot_images", bootImagePillar);
            pillar.add("images", imagePillar);

            for (File f : Paths.get(SUMA_PILLAR_IMAGES_DATA_PATH).toFile().listFiles()) {
                if (f.getName().startsWith(PILLAR_IMAGE_DATA_FILE_PREFIX + "-" + bundle.getBasename())) {
                    f.delete();
                }
            }

            Path filePath = Paths.get(SUMA_PILLAR_IMAGES_DATA_PATH).resolve(
                    getImagePillarFileName(bundle)
            );

            SaltStateGenerator saltStateGenerator = new SaltStateGenerator(filePath.toFile());
            saltStateGenerator.generate(pillar);
        }
        catch (IOException e) {
            LOG.error(e.getMessage(), e);
        }
    }

    private Map<String, Object> generateImagePillar(OSImageInspectSlsResult.Image image,
                                                        OSImageInspectSlsResult.Bundle bundle,
                                                        OSImageInspectSlsResult.BootImage bootImage, String urlBase,
                                                        String name, String version, String localPath) {
        Map<String, Object> imagePillar = new TreeMap<String, Object>();
        Map<String, Object> imagePillarBase = new TreeMap<String, Object>();
        Map<String, Object> imagePillarDetails = new TreeMap<String, Object>();
        Map<String, Object> imagePillarDetailsSync = new TreeMap<String, Object>();

        imagePillarDetailsSync.put("bundle_hash", bundle.getChecksum().getChecksum());
        imagePillarDetailsSync.put("bundle_url", urlBase + "/" + bundle.getFilename());
        imagePillarDetailsSync.put("local_path", localPath);

        imagePillarDetails.put("arch", image.getArch());
        imagePillarDetails.put("basename", image.getBasename());
        imagePillarDetails.put("boot_image", name + "-" + version);
        imagePillarDetails.put("filename", image.getFilename());
        imagePillarDetails.put("filepath", image.getFilepath());
        imagePillarDetails.put("fstype", image.getFstype());
        imagePillarDetails.put("hash", image.getHash());
        imagePillarDetails.put("inactive", Boolean.FALSE);
        imagePillarDetails.put("name", image.getName());
        imagePillarDetails.put("size", image.getSize());
        imagePillarDetails.put("sync", imagePillarDetailsSync);
        imagePillarDetails.put("type", image.getType());
        imagePillarDetails.put("url", "ftp://ftp/" + localPath + "/" + image.getFilename());

        imagePillarBase.put(version, imagePillarDetails);
        imagePillar.put(name, imagePillarBase);
        return imagePillar;
    }

    private Map<String, Object> generateBootImagePillar(OSImageInspectSlsResult.BootImage bootImage,
                                                        String bootImageName,
                                                        String systemLocalPath, String bootLocalPath) {
        Map<String, Object> bootImagePillar = new TreeMap<String, Object>();
        Map<String, Object> bootImagePillarBase = new TreeMap<String, Object>();
        Map<String, Object> bootImagePillarInitrd = new TreeMap<String, Object>();
        Map<String, Object> bootImagePillarKernel = new TreeMap<String, Object>();
        Map<String, Object> bootImagePillarSync = new TreeMap<String, Object>();

        bootImagePillarBase.put("arch", bootImage.getArch());
        bootImagePillarBase.put("basename", bootImage.getBasename());
        bootImagePillarBase.put("name", bootImage.getName());

        bootImagePillarInitrd.put("filename", bootImage.getInitrd().getFilename());
        bootImagePillarInitrd.put("hash", bootImage.getInitrd().getHash());
        bootImagePillarInitrd.put("size", bootImage.getInitrd().getSize());
        bootImagePillarInitrd.put("version", bootImage.getInitrd().getVersion());
        bootImagePillarInitrd.put("url", "tftp://tftp/boot/" + bootImageName + '/' +
                bootImage.getInitrd().getFilename());

        bootImagePillarKernel.put("filename", bootImage.getKernel().getFilename());
        bootImagePillarKernel.put("hash", bootImage.getKernel().getHash());
        bootImagePillarKernel.put("size", bootImage.getKernel().getSize());
        bootImagePillarKernel.put("version", bootImage.getKernel().getVersion());
        bootImagePillarKernel.put("url", "tftp://tftp/boot/" + bootImageName + '/' +
                bootImage.getKernel().getFilename());

        bootImagePillarSync.put("local_path", bootLocalPath);
        bootImagePillarSync.put("kernel_link", "../../" + systemLocalPath + '/' + bootImage.getKernel().getFilename());
        bootImagePillarSync.put("initrd_link", "../../" + systemLocalPath + '/' + bootImage.getInitrd().getFilename());

        bootImagePillarBase.put("initrd", bootImagePillarInitrd);
        bootImagePillarBase.put("kernel", bootImagePillarKernel);
        bootImagePillarBase.put("sync", bootImagePillarSync);

        bootImagePillar.put(bootImageName, bootImagePillarBase);
        return bootImagePillar;
    }

    private String getImagePillarFileName(OSImageInspectSlsResult.Bundle bundle) {
        return PILLAR_IMAGE_DATA_FILE_PREFIX + "-" + bundle.getBasename() + "-" +
                bundle.getId().replace('.', '-') + "." + PILLAR_IMAGE_DATA_FILE_EXT;
    }

    /**
     * Generate OS Image specific pillar for Branch group, containing synced flag
     * @param branch the ServerGroup for which the pillar is created
     * @param name the OS image name
     * @param version the OS image version
     */
    public void createImageSyncedPillar(ServerGroup branch, String name, String version) {

        try {
            SaltPillar pillar = new SaltPillar();
            Map<String, Object> imagePillarDetails = Collections.singletonMap("synced", true);
            Map<String, Object> imagePillarBase = Collections.singletonMap(version, imagePillarDetails);
            Map<String, Object> imagePillar = Collections.singletonMap(name, imagePillarBase);

            pillar.add("images", imagePillar);

            Path dirPath = Paths.get(SUMA_PILLAR_IMAGES_DATA_PATH).resolve(
                    "group" + branch.getId().toString()
            );

            Path filePath = dirPath.resolve(
                    name.replace('.', '-') + "-" + version + "." + PILLAR_IMAGE_DATA_FILE_EXT
            );

            if (!dirPath.toFile().exists()) {
                dirPath.toFile().mkdirs();
            }

            SaltStateGenerator saltStateGenerator = new SaltStateGenerator(filePath.toFile());
            saltStateGenerator.generate(pillar);
        }
        catch (IOException e) {
            LOG.error(e.getMessage(), e);
        }

    }

    /**
     * Remove OS Image specific pillar for Branch group
     * @param branch the ServerGroup
     * @param name the OS image name
     * @param version the OS image version
     */
    public void removeImageSyncedPillar(ServerGroup branch, String name, String version) {
        try {
            Path dirPath = Paths.get(SUMA_PILLAR_IMAGES_DATA_PATH).resolve(
                    "group" + branch.getId().toString()
            );

            Path filePath = dirPath.resolve(
                    name.replace('.', '-') + "-" + version + "." + PILLAR_IMAGE_DATA_FILE_EXT
            );

            Files.deleteIfExists(filePath);
        }
        catch (IOException e) {
            LOG.error(e.getMessage(), e);
        }
    }


    /**
     * Remove the config channel assignments for minion server.
     * @param minion the minion server
     */
    public void removeConfigChannelAssignments(MinionServer minion) {
        removeConfigChannelAssignments(getServerStateFileName(minion.getMachineId()));
    }

    /**
     * Remove the config channel assignments for server group.
     * @param group the server group
     */
    public void removeConfigChannelAssignments(ServerGroup group) {
        removeConfigChannelAssignments(getGroupStateFileName(group.getId()));
    }

    private void removeActionChains(MinionServer minion) {
        SaltActionChainGeneratorService.INSTANCE.removeActionChainSLSFilesForMinion(minion, Optional.empty());
    }

    /**
     * Remove the config channel assignments for an organization.
     * @param org the organization
     */
    public void removeConfigChannelAssignments(Org org) {
        removeConfigChannelAssignments(getOrgStateFileName(org.getId()));
    }

    private void removeConfigChannelAssignments(String file) {
        Path baseDir = suseManagerStatesFilesRoot.resolve(SALT_CONFIG_STATES_DIR);
        Path filePath = baseDir.resolve(defaultExtension(file));

        try {
            Files.deleteIfExists(filePath);
        }
        catch (IOException e) {
            LOG.error(e.getMessage(), e);
            throw new RuntimeException(e);
        }
    }

    /**
     * Generate .sls file to assign config channels to a configurable entity.
     * @param revision the state revision of the configurable
     */
    public void generateConfigState(StateRevision revision) {
        generateConfigState(revision, suseManagerStatesFilesRoot);
    }

    /**
     * Generate .sls file to assign config channels to a configurable entity.
     * @param revision the state revision of the configurable
     * @param statePath the directory where to generate the files
     */
    public void generateConfigState(StateRevision revision, Path statePath) {
        if (revision instanceof ServerStateRevision) {
            generateServerConfigState((ServerStateRevision) revision, statePath);
        }
        else if (revision instanceof ServerGroupStateRevision) {
            generateGroupConfigState((ServerGroupStateRevision) revision, statePath);
        }
        else if (revision instanceof OrgStateRevision) {
            generateOrgConfigState((OrgStateRevision) revision, statePath);
        }
    }

    /**
     * Generate .sls file to assign config channels to a server.
     * @param serverStateRevision the state revision of a server
     * @param statePath the directory where to generate the files
     */
    private void generateServerConfigState(ServerStateRevision serverStateRevision, Path statePath) {
        serverStateRevision.getServer().asMinionServer().ifPresent(minion -> {
            LOG.debug("Generating config channel SLS file for server: " + minion.getId());

            generateConfigStates(serverStateRevision, getServerStateFileName(minion.getMachineId()), statePath);
        });
    }

    /**
     * Generate .sls file to assign config channels to a server group.
     * @param groupStateRevision the state revision of a server group
     * @param statePath the directory where to generate the files
     */
    private void generateGroupConfigState(ServerGroupStateRevision groupStateRevision,
                                         Path statePath) {
        ServerGroup group = groupStateRevision.getGroup();
        LOG.debug("Generating config channel SLS file for server group: " + group.getId());

        generateConfigStates(groupStateRevision, getGroupStateFileName(group.getId()), statePath);
    }

    /**
     * Generate .sls file to assign config channels to an org.
     * @param orgStateRevision the state revision of an org
     * @param statePath the directory where to generate the sls files
     */
    private void generateOrgConfigState(OrgStateRevision orgStateRevision, Path statePath) {
        Org org = orgStateRevision.getOrg();
        LOG.debug("Generating config channel SLS file for organization: " + org.getId());

        generateConfigStates(orgStateRevision, getOrgStateFileName(org.getId()), statePath);
    }

    private void generateConfigStates(StateRevision stateRevision, String fileName, Path statePath) {
        generateStateAssignmentFile(fileName, stateRevision.getConfigChannels(), statePath);
    }

    private void generateStateAssignmentFile(String fileName, List<ConfigChannel> states, Path statePath) {
        ConfigChannelSaltManager confChannelSaltManager =
                ConfigChannelSaltManager.getInstance();
        Path baseDir = statePath.resolve(SALT_CONFIG_STATES_DIR);
        List<String> stateNames =
                states.stream().map(confChannelSaltManager::getChannelStateName).collect(Collectors.toList());
        try {
            Files.createDirectories(baseDir);
            Path filePath = baseDir.resolve(defaultExtension(fileName));
            com.suse.manager.webui.utils.SaltStateGenerator saltStateGenerator =
                    new com.suse.manager.webui.utils.SaltStateGenerator(filePath.toFile());
            saltStateGenerator.generate(new SaltConfigChannelState(stateNames));
        }
        catch (IOException e) {
            LOG.error(e.getMessage(), e);
            throw new RuntimeException(e);
        }
    }

    /**
     * Remove pillars and config channels assignments of a server.
     * @param minion the minion
     */
    public void removeServer(MinionServer minion) {
        MinionPillarManager.INSTANCE.removePillar(minion.getMinionId());
        removeConfigChannelAssignments(minion);
        removeActionChains(minion);
    }

    /**
     * Remove config channels assignments of a group.
     * @param group the group
     */
    public void removeServerGroup(ServerGroup group) {
        removeConfigChannelAssignments(group);
    }

    /**
     * Remove config channels assignments of all servers in that org.
     * @param org the org
     */
    public void removeOrg(Org org) {
        MinionServerFactory.lookupByOrg(org.getId()).forEach(this::removeServer);
        removeConfigChannelAssignments(org);
    }

    /**
     * Regenerate config channel assignments for org, group and severs where
     * the given state is used.
     * @param configChannelIn the config channel
     */
    public void regenerateConfigStates(ConfigChannel configChannelIn) {
        StateFactory.StateRevisionsUsage usage = StateFactory
                .latestStateRevisionsByConfigChannel(configChannelIn);
        regenerateConfigStates(usage);
    }

    /**
     * Regenerate config channel assignments for org, group and severs for
     * the given usages.
     * @param usage config channel usages
     */
    public void regenerateConfigStates(StateFactory.StateRevisionsUsage usage) {
        usage.getServerStateRevisions().forEach(this::generateConfigState);
        usage.getServerGroupStateRevisions().forEach(this::generateConfigState);
        usage.getOrgStateRevisions().forEach(this::generateConfigState);
    }

    /**
     * Regenerate pillar with the new org and create a new state revision without
     * any package or config channels.
     * @param minion the migrated server
     * @param user the user performing the migration
     */
    public void migrateServer(MinionServer minion, User user) {
        // generate a new state revision without any package or config channels
        ServerStateRevision newStateRev = StateRevisionService.INSTANCE
                .cloneLatest(minion, user, false, false);
        StateFactory.save(newStateRev);

        // refresh pillar, config and package states
        MinionPillarManager.INSTANCE.generatePillar(minion);
        generateConfigState(newStateRev);
        StatesAPI.generateServerPackageState(minion);
    }

    private String getGroupStateFileName(long groupId) {
        return "group_" + groupId;
    }

    private String getOrgStateFileName(long orgId) {
        return "org_" + orgId;
    }


    private String getServerStateFileName(String digitalServerId) {
        return SALT_SERVER_STATE_FILE_PREFIX + digitalServerId;
    }


    /**
     * @param groupId the id of the server group
     * @return the name of the generated server group .sls file.
     */
    public String getServerGroupGeneratedStateName(long groupId) {
        return SALT_CONFIG_STATES_DIR + "." + getGroupStateFileName(groupId);
    }

    /**
     * @param generatedSlsRootIn the root path where state files are generated
     */
    public void setSuseManagerStatesFilesRoot(Path generatedSlsRootIn) {
        this.suseManagerStatesFilesRoot = generatedSlsRootIn;
    }

    /**
     * Generate state files for a new server group.
     * @param serverGroup the new server group
     */
    public void createServerGroup(ServerGroup serverGroup) {
        generateStateAssignmentFile(getGroupStateFileName(serverGroup.getId()), Collections.emptyList(),
                suseManagerStatesFilesRoot);
    }

    /**
     * Generate state files for a new org.
     * @param org the new org
     */
    public void createOrg(Org org) {
        generateStateAssignmentFile(getOrgStateFileName(org.getId()), Collections.emptyList(),
                suseManagerStatesFilesRoot);
    }


    /**
     * Expose some global configuration options as pillar data.
     * @param saltRootPath the directory where to generate the pillar data file
     */
    public void generateMgrConfPillar(Path saltRootPath) {
        boolean metataSigningEnabled = ConfigDefaults.get().isMetadataSigningEnabled();
        SaltPillar pillar = new SaltPillar();
        pillar.add("mgr_metadata_signing_enabled", metataSigningEnabled);
        Path filePath = saltRootPath.resolve("mgr_conf." + PILLAR_DATA_FILE_EXT);
        FileUtils.deleteFile(filePath);
        try {
            com.suse.manager.webui.utils.SaltStateGenerator saltStateGenerator =
                    new com.suse.manager.webui.utils.SaltStateGenerator(filePath.toFile());
            saltStateGenerator.generate(pillar);
        }
        catch (IOException e) {
            LOG.error("Failed to generate pillar data into " + filePath, e);
            throw new RuntimeException(e);
        }
    }
}
