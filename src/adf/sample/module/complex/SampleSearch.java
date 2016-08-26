package adf.sample.module.complex;

import adf.agent.communication.MessageManager;
import adf.agent.communication.standard.bundle.MessageUtil;
import adf.agent.communication.standard.bundle.information.*;
import adf.agent.communication.standard.bundle.topdown.*;
import adf.agent.develop.DevelopData;
import adf.agent.info.AgentInfo;
import adf.agent.info.ScenarioInfo;
import adf.agent.info.WorldInfo;
import adf.agent.module.ModuleManager;
import adf.agent.precompute.PrecomputeData;
import adf.component.communication.CommunicationMessage;
import adf.component.module.algorithm.Clustering;
import adf.component.module.algorithm.PathPlanning;
import adf.component.module.complex.Search;
import adf.sample.SampleModuleKey;
import rescuecore2.standard.entities.*;
import rescuecore2.worldmodel.EntityID;

import java.util.*;

import static rescuecore2.standard.entities.StandardEntityURN.*;

public class SampleSearch extends Search {

    private Collection<EntityID> unexploredBuildings;
    private EntityID result;

    private boolean hasTask;
    private boolean isBroadcast;
    private boolean isCommand;
    private boolean isScout;
    private Collection<EntityID> scoutEntities;

    public SampleSearch(AgentInfo ai, WorldInfo wi, ScenarioInfo si, ModuleManager moduleManager, DevelopData developData) {
        super(ai, wi, si, moduleManager, developData);
        this.unexploredBuildings = new HashSet<>();
        this.hasTask = false;
        this.isBroadcast = false;
        this.isCommand = false;
        this.isScout = false;
        this.scoutEntities = new HashSet<>();
    }

    @Override
    public Search updateInfo(MessageManager messageManager) {
        this.checkTask(messageManager);
        this.initUnexploredBuildings();
        this.reflectMessage(messageManager);
        this.sendMessage(messageManager);
        return this;
    }

    private void initUnexploredBuildings() {
        Clustering clustering = null;
        if (this.agentInfo.me().getStandardURN() == AMBULANCE_TEAM) {
            clustering = this.moduleManager.getModule(SampleModuleKey.AMBULANCE_MODULE_CLUSTERING);
        } else if (this.agentInfo.me().getStandardURN() == FIRE_BRIGADE) {
            clustering = this.moduleManager.getModule(SampleModuleKey.FIRE_MODULE_CLUSTERING);
        } else if (this.agentInfo.me().getStandardURN() == POLICE_FORCE) {
            clustering = this.moduleManager.getModule(SampleModuleKey.POLICE_MODULE_CLUSTERING);
        }
        if(clustering != null) {
            int clusterIndex = clustering.getClusterIndex(this.agentInfo.getID());
            if(this.unexploredBuildings.isEmpty()) {
                this.initUnexploredBuildings(clustering, clusterIndex);
            }
            this.unexploredBuildings.removeAll(this.worldInfo.getChanged().getChangedEntities());
            if(this.unexploredBuildings.isEmpty()) {
                this.initUnexploredBuildings(clustering, clusterIndex);
            }
        } else {
            if(this.unexploredBuildings.isEmpty()) {
                this.initUnexploredBuildings(
                        this.worldInfo.getEntitiesOfType(
                                BUILDING,
                                GAS_STATION,
                                AMBULANCE_CENTRE,
                                FIRE_STATION,
                                POLICE_OFFICE
                        )
                );
            }
            this.unexploredBuildings.removeAll(this.worldInfo.getChanged().getChangedEntities());
            if(this.unexploredBuildings.isEmpty()) {
                this.initUnexploredBuildings(
                        this.worldInfo.getEntitiesOfType(
                                BUILDING,
                                GAS_STATION,
                                AMBULANCE_CENTRE,
                                FIRE_STATION,
                                POLICE_OFFICE
                        )
                );
            }
        }
    }

    private void initUnexploredBuildings(Clustering clustering, int clusterIndex) {
        this.initUnexploredBuildings(clustering.getClusterEntities(clusterIndex));
    }

    private void initUnexploredBuildings(Collection<StandardEntity> entities) {
        for(StandardEntity entity : entities) {
            if(entities instanceof Building) {
                this.unexploredBuildings.add(entity.getID());
            }
        }
    }

    private void checkTask(MessageManager messageManager) {
        if(this.hasTask) {
            if(this.isScout) {
                this.scoutEntities.removeAll(this.worldInfo.getChanged().getChangedEntities());
                if (this.scoutEntities.isEmpty()) {
                    this.hasTask = false;
                    this.isBroadcast = false;
                    this.isCommand = false;
                    this.isScout = false;
                    messageManager.addMessage(new MessageReport(true, true));
                }
            } else { //action move
                EntityID removeID = null;
                for(EntityID entityID : this.scoutEntities) {
                    if(entityID.getValue() == this.agentInfo.getPosition().getValue()) {
                        removeID = entityID;
                        break;
                    }
                }
                if(removeID != null) {
                    this.scoutEntities.remove(removeID);
                }
                if (this.scoutEntities.isEmpty()) {
                    this.hasTask = false;
                    this.isBroadcast = false;
                    this.isCommand = false;
                    this.isScout = false;
                    messageManager.addMessage(new MessageReport(true, true));
                }
            }
        }
        if(this.hasTask) {
            return;
        }
        List<StandardEntity> agents = new ArrayList<>(this.worldInfo.getEntitiesOfType(
                StandardEntityURN.AMBULANCE_TEAM,
                StandardEntityURN.FIRE_BRIGADE,
                StandardEntityURN.POLICE_OFFICE
        ));

        StandardEntityURN agentURN = this.agentInfo.me().getStandardURN();
        if(agentURN == StandardEntityURN.AMBULANCE_TEAM) {
            this.checkCommandAmbulance(messageManager, agents);
        } else if(agentURN == StandardEntityURN.FIRE_BRIGADE) {
            this.checkCommandFire(messageManager, agents);
        } else if(agentURN == StandardEntityURN.POLICE_FORCE) {
            this.checkCommandPolice(messageManager, agents);
        }

        this.checkCommandScout(messageManager, agents);
    }

    private void checkCommandAmbulance(MessageManager messageManager, List<StandardEntity> agents) {
        EntityID agentID = this.agentInfo.getID();
        Collection<EntityID> controlEntityIDs = this.worldInfo.getEntityIDsOfType(StandardEntityURN.AMBULANCE_CENTRE);
        for(CommunicationMessage message : messageManager.getReceivedMessageList(CommandAmbulance.class)) {
            CommandAmbulance command = (CommandAmbulance)message;
            if(agentID.getValue() == command.getToID().getValue()) {
                if(command.getAction() == CommandAmbulance.ACTION_MOVE) {
                    this.hasTask = true;
                    this.isBroadcast = false;
                    this.isCommand = controlEntityIDs.contains(command.getSenderID());
                    this.isScout = false;
                    this.scoutEntities.add(command.getTargetID());
                    return;
                }
            }
            if(command.isBroadcast()) {
                if(command.getAction() == CommandAmbulance.ACTION_MOVE) {
                    agents.sort(new DistanceSorter(this.worldInfo, this.agentInfo.me()));
                    StandardEntity nearAgent = agents.get(0);
                    if(nearAgent.getID().getValue() == this.agentInfo.getID().getValue()) {
                        this.hasTask = true;
                        this.isBroadcast = true;
                        this.isCommand = false;
                        this.isScout = false;
                        this.scoutEntities.add(command.getTargetID());
                        return;
                    }
                }
            }
        }
    }

    private void checkCommandFire(MessageManager messageManager, List<StandardEntity> agents) {
        EntityID agentID = this.agentInfo.getID();
        Collection<EntityID> controlEntityIDs = this.worldInfo.getEntityIDsOfType(StandardEntityURN.FIRE_STATION);
        for(CommunicationMessage message : messageManager.getReceivedMessageList(CommandFire.class)) {
            CommandFire command = (CommandFire) message;
            if(agentID.getValue() == command.getToID().getValue()) {
                if(command.getAction() == CommandFire.ACTION_MOVE) {
                    this.hasTask = true;
                    this.isBroadcast = false;
                    this.isCommand = controlEntityIDs.contains(command.getSenderID());
                    this.isScout = false;
                    this.scoutEntities.add(command.getTargetID());
                    return;
                }
            }
            if(command.isBroadcast()) {
                if(command.getAction() == CommandFire.ACTION_MOVE) {
                    agents.sort(new DistanceSorter(this.worldInfo, this.agentInfo.me()));
                    StandardEntity nearAgent = agents.get(0);
                    if(nearAgent.getID().getValue() == this.agentInfo.getID().getValue()) {
                        this.hasTask = true;
                        this.isBroadcast = true;
                        this.isCommand = false;
                        this.isScout = false;
                        this.scoutEntities.add(command.getTargetID());
                        return;
                    }
                }
            }
        }
    }

    private void checkCommandPolice(MessageManager messageManager, List<StandardEntity> agents) {
        EntityID agentID = this.agentInfo.getID();
        Collection<EntityID> controlEntityIDs = this.worldInfo.getEntityIDsOfType(StandardEntityURN.POLICE_OFFICE);
        for(CommunicationMessage message : messageManager.getReceivedMessageList(CommandPolice.class)) {
            CommandPolice command = (CommandPolice) message;
            if(agentID.getValue() == command.getToID().getValue()) {
                if(command.getAction() == CommandPolice.ACTION_MOVE) {
                    this.hasTask = true;
                    this.isBroadcast = false;
                    this.isCommand = controlEntityIDs.contains(command.getSenderID());
                    this.isScout = false;
                    this.scoutEntities.add(command.getTargetID());
                    return;
                }
            }
            if(command.isBroadcast()) {
                if(command.getAction() == CommandPolice.ACTION_MOVE) {
                    agents.sort(new DistanceSorter(this.worldInfo, this.agentInfo.me()));
                    StandardEntity nearAgent = agents.get(0);
                    if(nearAgent.getID().getValue() == this.agentInfo.getID().getValue()) {
                        this.hasTask = true;
                        this.isBroadcast = true;
                        this.isCommand = false;
                        this.isScout = false;
                        this.scoutEntities.add(command.getTargetID());
                        return;
                    }
                }
            }
        }
    }

    private void checkCommandScout(MessageManager messageManager, List<StandardEntity> agents) {
        for(CommunicationMessage message : messageManager.getReceivedMessageList(CommandScout.class)) {
            CommandScout command = (CommandScout)message;
            if(command.isToIDDefined() && command.getToID().getValue() == this.agentInfo.getID().getValue()) {
                this.hasTask = true;
                this.isBroadcast = false;
                this.isCommand = false;
                this.isScout = true;
                this.scoutEntities.addAll(this.worldInfo.getObjectIDsInRange(command.getTargetID(), command.getRange()));
                return;
            }
            if(command.isBroadcast()) {
                if(this.hasTask) {
                    continue;
                }
                agents.sort(new DistanceSorter(this.worldInfo, this.agentInfo.me()));
                StandardEntity nearAgent = agents.get(0);
                if(nearAgent.getID().getValue() == this.agentInfo.getID().getValue()) {
                    this.hasTask = true;
                    this.isBroadcast = true;
                    this.isCommand = false;
                    this.isScout = true;
                    this.scoutEntities.addAll(this.worldInfo.getObjectIDsInRange(command.getTargetID(), command.getRange()));
                    return;
                }
            }
        }
    }

    private void reflectMessage(MessageManager messageManager) {
        Set<EntityID> changedEntities = this.worldInfo.getChanged().getChangedEntities();
        for(CommunicationMessage message : messageManager.getReceivedMessageList()) {
            Class<? extends CommunicationMessage> messageClass = message.getClass();
            if(messageClass == MessageBuilding.class) {
                MessageBuilding mb = (MessageBuilding)message;
                if(!changedEntities.contains(mb.getBuildingID())) {
                    MessageUtil.reflectMessage(this.worldInfo, mb);
                }
            } else if(messageClass == MessageRoad.class) {
                MessageRoad mr = (MessageRoad)message;
                if(mr.isBlockadeDefined() && !changedEntities.contains(mr.getBlockadeID())) {
                    MessageUtil.reflectMessage(this.worldInfo, mr);
                }
            } else if(messageClass == MessageCivilian.class) {
                MessageCivilian mc = (MessageCivilian) message;
                if(!changedEntities.contains(mc.getAgentID())){
                    MessageUtil.reflectMessage(this.worldInfo, mc);
                }
            } else if(messageClass == MessageAmbulanceTeam.class) {
                MessageAmbulanceTeam mat = (MessageAmbulanceTeam)message;
                if(!changedEntities.contains(mat.getAgentID())) {
                    MessageUtil.reflectMessage(this.worldInfo, mat);
                }
            } else if(messageClass == MessageFireBrigade.class) {
                MessageFireBrigade mfb = (MessageFireBrigade) message;
                if(!changedEntities.contains(mfb.getAgentID())) {
                    MessageUtil.reflectMessage(worldInfo, mfb);
                }
            } else if(messageClass == MessagePoliceForce.class) {
                MessagePoliceForce mpf = (MessagePoliceForce) message;
                if(!changedEntities.contains(mpf.getAgentID())) {
                    MessageUtil.reflectMessage(worldInfo, mpf);
                }
            }
        }
    }

    private void sendMessage(MessageManager messageManager) {
        for(EntityID id : this.worldInfo.getChanged().getChangedEntities()) {
            StandardEntity entity = this.worldInfo.getEntity(id);
            if(entity instanceof Building) {
                Building building = (Building)entity;
                if(building.isFierynessDefined() && building.isOnFire()) {
                    messageManager.addMessage(new MessageBuilding(true, building));
                }
            } else if(entity instanceof Road) {
                Road road = (Road)entity;
                List<EntityID> blockades = road.getBlockades();
                if(blockades == null || blockades.isEmpty()) {
                    messageManager.addMessage(new MessageRoad(true, road, null, true));
                }
            } else if(entity.getStandardURN() == CIVILIAN) {
                Civilian civilian = (Civilian)entity;
                if(this.needSend(civilian)) {
                    messageManager.addMessage(new MessageCivilian(true, civilian));
                }
            } else if(entity.getStandardURN() == BLOCKADE) {
                Blockade blockade = (Blockade)entity;
                Road road = (Road)this.worldInfo.getEntity(blockade.getPosition());
                StandardEntity rollbackEntity = this.worldInfo.getEntity(-1, id);
                if(rollbackEntity == null) {
                    messageManager.addMessage(new MessageRoad(true, road, blockade, false));
                } else {
                    Blockade rollbackBlockade = (Blockade) rollbackEntity;
                    if (blockade.getRepairCost() != rollbackBlockade.getRepairCost()) {
                        messageManager.addMessage(new MessageRoad(true, road, blockade, false));
                    }
                }
            }
        }
    }

    private boolean needSend(Civilian civilian) {
        if(civilian.isBuriednessDefined() && civilian.getBuriedness() > 0) {
            return true;
        }
        else if(civilian.isDamageDefined() && civilian.getDamage() > 0
                && civilian.isPositionDefined() && this.worldInfo.getEntity(civilian.getPosition()) instanceof Road){
            return true;
        }
        return false;
    }

    @Override
    public Search calc() {
        PathPlanning pathPlanning = null;
        if(this.agentInfo.me().getStandardURN() == AMBULANCE_TEAM) {
            pathPlanning = this.moduleManager.getModule(SampleModuleKey.AMBULANCE_MODULE_PATH_PLANNING);
        } else if(this.agentInfo.me().getStandardURN() == FIRE_BRIGADE) {
            pathPlanning = this.moduleManager.getModule(SampleModuleKey.FIRE_MODULE_PATH_PLANNING);
        } else if(this.agentInfo.me().getStandardURN() == POLICE_FORCE) {
            pathPlanning = this.moduleManager.getModule(SampleModuleKey.POLICE_MODULE_PATH_PLANNING);
        }
        if(pathPlanning != null) {
            if(this.hasTask) {
                List<EntityID> path = pathPlanning.setFrom(this.agentInfo.getPosition())
                        .setDestination(this.scoutEntities)
                        .calc().getResult();
                if (path != null) {
                    this.result = path.get(path.size() - 1);
                }
                return this;
            }
            List<EntityID> path = pathPlanning.setFrom(this.agentInfo.getPosition())
                    .setDestination(this.unexploredBuildings)
                    .calc()
                    .getResult();
            if (path != null) {
                this.result = path.get(path.size() - 1);
            }
        }
        return this;
    }

    @Override
    public EntityID getTarget() {
        return this.result;
    }

    @Override
    public Search precompute(PrecomputeData precomputeData) {
        super.precompute(precomputeData);
        return this;
    }

    @Override
    public Search resume(PrecomputeData precomputeData) {
        super.resume(precomputeData);
        this.worldInfo.requestRollback();
        return this;
    }

    @Override
    public Search preparate() {
        super.preparate();
        this.worldInfo.requestRollback();
        return this;
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