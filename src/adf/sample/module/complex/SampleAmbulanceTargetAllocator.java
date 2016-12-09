package adf.sample.module.complex;

import adf.agent.communication.MessageManager;
import adf.agent.communication.standard.bundle.MessageUtil;
import adf.agent.communication.standard.bundle.centralized.CommandAmbulance;
import adf.agent.communication.standard.bundle.centralized.MessageReport;
import adf.agent.communication.standard.bundle.information.MessageAmbulanceTeam;
import adf.agent.communication.standard.bundle.information.MessageCivilian;
import adf.agent.communication.standard.bundle.information.MessageFireBrigade;
import adf.agent.communication.standard.bundle.information.MessagePoliceForce;
import adf.agent.develop.DevelopData;
import adf.agent.info.AgentInfo;
import adf.agent.info.ScenarioInfo;
import adf.agent.info.WorldInfo;
import adf.agent.module.ModuleManager;
import adf.agent.precompute.PrecomputeData;
import adf.component.communication.CommunicationMessage;
import adf.component.module.complex.AmbulanceTargetAllocator;
import rescuecore2.standard.entities.*;
import rescuecore2.worldmodel.EntityID;

import java.util.*;

import static rescuecore2.standard.entities.StandardEntityURN.*;

public class SampleAmbulanceTargetAllocator extends AmbulanceTargetAllocator {
    private Collection<EntityID> priorityHumans;
    private Collection<EntityID> targetHumans;

    private Map<EntityID, AmbulanceTeamInfo> ambulanceTeamInfoMap;

    public SampleAmbulanceTargetAllocator(AgentInfo ai, WorldInfo wi, ScenarioInfo si, ModuleManager moduleManager, DevelopData developData) {
        super(ai, wi, si, moduleManager, developData);
        this.priorityHumans = new HashSet<>();
        this.targetHumans = new HashSet<>();
        this.ambulanceTeamInfoMap = new HashMap<>();
    }

    @Override
    public AmbulanceTargetAllocator resume(PrecomputeData precomputeData) {
        super.resume(precomputeData);
        if(this.getCountResume() >= 2) {
            return this;
        }
        for(EntityID id : this.worldInfo.getEntityIDsOfType(StandardEntityURN.AMBULANCE_TEAM)) {
            this.ambulanceTeamInfoMap.put(id, new AmbulanceTeamInfo(id));
        }
        return this;
    }

    @Override
    public AmbulanceTargetAllocator preparate() {
        super.preparate();
        if(this.getCountPrecompute() >= 2) {
            return this;
        }
        for(EntityID id : this.worldInfo.getEntityIDsOfType(StandardEntityURN.AMBULANCE_TEAM)) {
            this.ambulanceTeamInfoMap.put(id, new AmbulanceTeamInfo(id));
        }
        return this;
    }

    @Override
    public Map<EntityID, EntityID> getResult() {
        return this.convertToResult(this.ambulanceTeamInfoMap);
    }

    @Override
    public AmbulanceTargetAllocator calc() {
        List<StandardEntity> agents = this.getAgents(this.ambulanceTeamInfoMap);
        int currentTime = this.agentInfo.getTime();
        Collection<EntityID> removes = new ArrayList<>();

        for(EntityID target : this.priorityHumans) {
            if(agents.size() > 0) {
                StandardEntity targetEntity = this.worldInfo.getEntity(target);
                if (targetEntity != null && targetEntity instanceof Human && ((Human)targetEntity).isPositionDefined()) {
                    agents.sort(new DistanceSorter(this.worldInfo, targetEntity));
                    StandardEntity result = agents.remove(0);
                    AmbulanceTeamInfo info = this.ambulanceTeamInfoMap.get(result.getID());
                    if (info != null) {
                        info.canNewAction = false;
                        info.target = target;
                        info.commandTime = currentTime;
                        this.ambulanceTeamInfoMap.put(result.getID(), info);
                        removes.add(target);
                    }
                }
            }
        }
        this.priorityHumans.removeAll(removes);
        removes.clear();

        for(EntityID target : this.targetHumans) {
            if(agents.size() > 0) {
                StandardEntity targetEntity = this.worldInfo.getEntity(target);
                if (targetEntity != null && targetEntity instanceof Human && ((Human)targetEntity).isPositionDefined()) {
                    agents.sort(new DistanceSorter(this.worldInfo, targetEntity));
                    StandardEntity result = agents.remove(0);
                    AmbulanceTeamInfo info = this.ambulanceTeamInfoMap.get(result.getID());
                    if(info != null) {
                        info.canNewAction = false;
                        info.target = target;
                        info.commandTime = currentTime;
                        this.ambulanceTeamInfoMap.put(result.getID(), info);
                        removes.add(target);
                    }
                }
            }
        }
        this.targetHumans.removeAll(removes);
        return this;
    }

    @Override
    public AmbulanceTargetAllocator updateInfo(MessageManager messageManager) {
        super.updateInfo(messageManager);
        if(this.getCountUpdateInfo() >= 2) {
            return this;
        }
        int currentTime = this.agentInfo.getTime();

        for(CommunicationMessage message : messageManager.getReceivedMessageList()) {
            Class<? extends CommunicationMessage> messageClass = message.getClass();
            if(messageClass == MessageCivilian.class) {
                this.updateFromMessage((MessageCivilian) message);

            } else if(messageClass == MessageFireBrigade.class) {
                this.updateFromMessage((MessageFireBrigade)message);

            } else if(messageClass == MessagePoliceForce.class) {
                this.updateFromMessage((MessagePoliceForce)message);

            } else if(messageClass == MessageAmbulanceTeam.class) {
                this.updateFromMessage(currentTime, (MessageAmbulanceTeam)message);

            } else if(messageClass == CommandAmbulance.class) {
                this.updateFromMessage((CommandAmbulance)message);

            } else if(messageClass == MessageReport.class) {
                this.updateFromMessage((MessageReport)message);
            }
        }
        return this;
    }

    private void updateFromMessage(CommandAmbulance command) {
        if(command.getAction() == CommandAmbulance.ACTION_RESCUE && command.isBroadcast()) {
            this.priorityHumans.add(command.getTargetID());
            this.targetHumans.add(command.getTargetID());
        } else if(command.getAction() == CommandAmbulance.ACTION_LOAD && command.isBroadcast()) {
            this.priorityHumans.add(command.getTargetID());
            this.targetHumans.add(command.getTargetID());
        }
    }

    private void updateFromMessage(int currentTime, MessageAmbulanceTeam message) {
        AmbulanceTeam ambulance = MessageUtil.reflectMessage(this.worldInfo, message);
        if(ambulance.isBuriednessDefined() && ambulance.getBuriedness() > 0) {
            this.priorityHumans.add(message.getAgentID());
        }else {
            this.priorityHumans.remove(message.getAgentID());
            this.targetHumans.remove(message.getAgentID());
        }
        AmbulanceTeamInfo info = this.ambulanceTeamInfoMap.get(message.getAgentID());
        if(info == null) {
            info = new AmbulanceTeamInfo(message.getAgentID());
        }
        if(currentTime >= info.commandTime + 2) {
            this.ambulanceTeamInfoMap.put(message.getAgentID(), this.updateAmbulanceTeamInfo(info, message));
        }
    }

    private void updateFromMessage(MessageCivilian message) {
        Civilian civilian = MessageUtil.reflectMessage(this.worldInfo, message);
        if(civilian.isBuriednessDefined() && civilian.getBuriedness() > 0) {
            this.targetHumans.add(message.getAgentID());
        } else {
            this.priorityHumans.remove(message.getAgentID());
            this.targetHumans.remove(message.getAgentID());
        }
    }

    private void updateFromMessage(MessageFireBrigade message) {
        FireBrigade fire = MessageUtil.reflectMessage(this.worldInfo, message);
        if(fire.isBuriednessDefined() && fire.getBuriedness() > 0) {
            this.priorityHumans.add(message.getAgentID());
        } else {
            this.priorityHumans.remove(message.getAgentID());
            this.targetHumans.remove(message.getAgentID());
        }
    }

    private void updateFromMessage(MessagePoliceForce message) {
        PoliceForce police = MessageUtil.reflectMessage(this.worldInfo, message);
        if(police.isBuriednessDefined() && police.getBuriedness() > 0) {
            this.priorityHumans.add(message.getAgentID());
        }else {
            this.priorityHumans.remove(message.getAgentID());
            this.targetHumans.remove(message.getAgentID());
        }
    }

    private void updateFromMessage(MessageReport report) {
        AmbulanceTeamInfo info = this.ambulanceTeamInfoMap.get(report.getSenderID());
        if(info != null && report.isDone()) {
            info.canNewAction = true;
            this.priorityHumans.remove(info.target);
            this.targetHumans.remove(info.target);
            info.target = null;
            this.ambulanceTeamInfoMap.put(info.agentID, info);
        }
    }

    private AmbulanceTeamInfo updateAmbulanceTeamInfo(AmbulanceTeamInfo info, MessageAmbulanceTeam message) {
        int action = message.getAction();
        if(message.isBuriednessDefined() && message.getBuriedness() > 0) {
            info.canNewAction = false;
            if (info.target != null) {
                this.targetHumans.add(info.target);
                info.target = null;
                info.commandTime = -1;
            }
        } else if(action == MessageAmbulanceTeam.ACTION_REST || action == MessageAmbulanceTeam.ACTION_RESCUE) {
            info.canNewAction = true;
            if (info.target != null) {
                this.targetHumans.add(info.target);
                info.target = null;
                info.commandTime = -1;
            }
        } else if(message.getAction() == MessageAmbulanceTeam.ACTION_LOAD) {
            info.canNewAction = false;
        } else if(message.getAction() == MessageAmbulanceTeam.ACTION_UNLOAD) {
            info.canNewAction = true;
            this.priorityHumans.remove(info.target);
            this.targetHumans.remove(info.target);
            info.target = null;
            info.commandTime = -1;
        } else if(message.getAction() == MessageAmbulanceTeam.ACTION_MOVE) {
            if (message.getTargetID() == null) {
                info.canNewAction = true;
                if (info.target != null) {
                    this.targetHumans.add(info.target);
                    info.target = null;
                    info.commandTime = -1;
                }
                return info;
            }
            StandardEntity messageTargetEntity = this.worldInfo.getEntity(message.getTargetID());
            if (messageTargetEntity != null) {
                if (messageTargetEntity instanceof Area) {
                    if (messageTargetEntity.getStandardURN() == REFUGE) {
                        info.canNewAction = false;
                        return info;
                    }
                    StandardEntity targetEntity = this.worldInfo.getEntity(info.target);
                    if (targetEntity == null) {
                        info.canNewAction = true;
                        info.target = null;
                        info.commandTime = -1;
                        return info;
                    }
                    if (targetEntity instanceof Human) {
                        targetEntity = this.worldInfo.getPosition((Human) targetEntity);
                        if (targetEntity == null) {
                            this.priorityHumans.remove(info.target);
                            this.targetHumans.remove(info.target);
                            info.canNewAction = true;
                            info.target = null;
                            info.commandTime = -1;
                            return info;
                        }
                    }
                    if (targetEntity.getID().getValue() == messageTargetEntity.getID().getValue()) {
                        info.canNewAction = false;
                    } else {
                        info.canNewAction = true;
                        if (info.target != null) {
                            this.targetHumans.add(info.target);
                            info.target = null;
                            info.commandTime = -1;
                        }
                    }
                } else if (messageTargetEntity instanceof Human) {
                    if (messageTargetEntity.getID().getValue() == info.target.getValue()) {
                        info.canNewAction = false;
                    } else {
                        info.canNewAction = true;
                        this.targetHumans.add(info.target);
                        this.targetHumans.add(messageTargetEntity.getID());
                        info.target = null;
                    }
                }
            }
        }
        return info;
    }

    private Map<EntityID, EntityID> convertToResult(Map<EntityID, AmbulanceTeamInfo> infoMap) {
        Map<EntityID, EntityID> result = new HashMap<>();
        for(EntityID id : infoMap.keySet()) {
            AmbulanceTeamInfo info = infoMap.get(id);
            if(info != null && info.target != null) {
                result.put(id, info.target);
            }
        }
        return result;
    }

    private List<StandardEntity> getAgents(Map<EntityID, AmbulanceTeamInfo> infoMap) {
        List<StandardEntity> result = new ArrayList<>();
        for(StandardEntity entity : this.worldInfo.getEntitiesOfType(StandardEntityURN.AMBULANCE_TEAM)) {
            AmbulanceTeamInfo info = infoMap.get(entity.getID());
            if(info != null && info.canNewAction && ((AmbulanceTeam)entity).isPositionDefined()) {
                result.add(entity);
            }
        }
        return result;
    }

    private class AmbulanceTeamInfo {
        EntityID agentID;
        EntityID target;
        boolean canNewAction;
        int commandTime;

        AmbulanceTeamInfo(EntityID id) {
            agentID = id;
            target = null;
            canNewAction = true;
            commandTime = -1;
        }
    }

    private class DistanceSorter implements Comparator<StandardEntity> {
        private StandardEntity reference;
        private WorldInfo worldInfo;

        DistanceSorter(WorldInfo wi, StandardEntity reference) {
            this.reference = reference;
            this.worldInfo = wi;
        }

        public int compare(StandardEntity a, StandardEntity b) {
            int d1 = this.worldInfo.getDistance(this.reference, a);
            int d2 = this.worldInfo.getDistance(this.reference, b);
            return d1 - d2;
        }
    }
}
