package adf.sample.module.complex;

import adf.agent.communication.MessageManager;
import adf.agent.communication.standard.bundle.MessageUtil;
import adf.agent.communication.standard.bundle.information.*;
import adf.agent.debug.DebugData;
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

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static rescuecore2.standard.entities.StandardEntityURN.*;

public class SampleSearch extends Search {

    private Collection<EntityID> unexploredBuildings;
    private EntityID result;

    public SampleSearch(AgentInfo ai, WorldInfo wi, ScenarioInfo si, ModuleManager moduleManager, DebugData debugData) {
        super(ai, wi, si, moduleManager, debugData);
        this.unexploredBuildings = new HashSet<>();
    }

    @Override
    public Search updateInfo(MessageManager messageManager) {
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
}