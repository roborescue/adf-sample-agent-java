package adf.sample.module.complex;

import adf.agent.communication.MessageManager;
import adf.agent.communication.standard.bundle.MessageUtil;
import adf.agent.communication.standard.bundle.information.*;
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
import rescuecore2.standard.entities.*;
import rescuecore2.worldmodel.EntityID;

import java.util.*;

import static rescuecore2.standard.entities.StandardEntityURN.*;

public class SampleSearch extends Search {

    private EntityID result;

    private Collection<EntityID> unexploredBuildingIDs;

    public SampleSearch(AgentInfo ai, WorldInfo wi, ScenarioInfo si, ModuleManager moduleManager, DevelopData developData) {
        super(ai, wi, si, moduleManager, developData);
        this.unexploredBuildingIDs = new HashSet<>();
    }

    @Override
    public Search calc() {
        this.result = null;

        PathPlanning pathPlanning = null;
        if(this.agentInfo.me().getStandardURN() == AMBULANCE_TEAM) {
            pathPlanning = this.moduleManager.getModule("TacticsAmbulance.PathPlanning");
        } else if(this.agentInfo.me().getStandardURN() == FIRE_BRIGADE) {
            pathPlanning = this.moduleManager.getModule("TacticsFire.PathPlanning");
        } else if(this.agentInfo.me().getStandardURN() == POLICE_FORCE) {
            pathPlanning = this.moduleManager.getModule("TacticsPolice.PathPlanning");
        }
        if(pathPlanning == null) {
            return this;
        }

        pathPlanning.setFrom(this.agentInfo.getPosition());
        pathPlanning.setDestination(this.unexploredBuildingIDs);
        List<EntityID> path = pathPlanning.calc().getResult();
        if(path != null && path.size() > 0) {
            this.result = path.get(path.size() - 1);
        }
        return this;
    }

    @Override
    public Search updateInfo(MessageManager messageManager) {
        this.reflectMessage(messageManager);
        this.sendMessage(messageManager);

        if(this.unexploredBuildingIDs.isEmpty()) {
            this.reset();
        }

        this.unexploredBuildingIDs.removeAll(worldInfo.getChanged().getChangedEntities());

        if(this.unexploredBuildingIDs.isEmpty()) {
            this.reset();
            this.unexploredBuildingIDs.removeAll(worldInfo.getChanged().getChangedEntities());
        }
        return this;
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    private void reset() {
        this.unexploredBuildingIDs.clear();
        StandardEntityURN agentURN = agentInfo.me().getStandardURN();
        Clustering clustering = null;
        if (agentURN == AMBULANCE_TEAM) {
            clustering = this.moduleManager.getModule("TacticsAmbulance.Clustering");
        } else if (agentURN == FIRE_BRIGADE) {
            clustering = this.moduleManager.getModule("TacticsFire.Clustering");
        } else if (agentURN == POLICE_FORCE) {
            clustering = this.moduleManager.getModule("TacticsPolice.Clustering");
        }

        boolean useClustering = true;
        Collection<StandardEntity> clusterEntities = null;
        if(clustering == null) {
            useClustering = false;
        } else {
            int clusterIndex = clustering.getClusterIndex(this.agentInfo.getID());
            clusterEntities = clustering.getClusterEntities(clusterIndex);
            if(clusterEntities == null || clusterEntities.isEmpty()) {
                useClustering = false;
            }
        }

        if(useClustering) {
            for(StandardEntity entity : clusterEntities) {
                if(entity instanceof Building && entity.getStandardURN() != REFUGE) {
                    this.unexploredBuildingIDs.add(entity.getID());
                }
            }
        } else {
            this.unexploredBuildingIDs.addAll(
                    this.worldInfo.getEntityIDsOfType(
                            BUILDING,
                            GAS_STATION,
                            AMBULANCE_CENTRE,
                            FIRE_STATION,
                            POLICE_OFFICE
                    )
            );
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    private void reflectMessage(MessageManager messageManager) {
        Set<EntityID> changedEntities = this.worldInfo.getChanged().getChangedEntities();
        changedEntities.add(this.agentInfo.getID());
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
                    MessageUtil.reflectMessage(this.worldInfo, mfb);
                }
            } else if(messageClass == MessagePoliceForce.class) {
                MessagePoliceForce mpf = (MessagePoliceForce) message;
                if(!changedEntities.contains(mpf.getAgentID())) {
                    MessageUtil.reflectMessage(this.worldInfo, mpf);
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

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

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