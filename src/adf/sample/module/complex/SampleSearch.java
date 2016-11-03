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

    private PathPlanning pathPlanning;
    private Clustering clustering;

    private Collection<EntityID> unsearchedBuildingIDs;

    public SampleSearch(AgentInfo ai, WorldInfo wi, ScenarioInfo si, ModuleManager moduleManager, DevelopData developData) {
        super(ai, wi, si, moduleManager, developData);
        StandardEntityURN agentURN = this.agentInfo.me().getStandardURN();
        switch  (si.getMode()) {
            case PRECOMPUTATION_PHASE:
                if(agentURN == AMBULANCE_TEAM) {
                    this.pathPlanning = this.moduleManager.getModule("SampleSearch.PathPlanning.Ambulance", "adf.sample.module.algorithm.SamplePathPlanning");
                    this.clustering = moduleManager.getModule("SampleSearch.Clustering.Ambulance", "adf.sample.module.algorithm.SampleKMeans");
                } else if(agentURN == FIRE_BRIGADE) {
                    this.pathPlanning = this.moduleManager.getModule("SampleSearch.PathPlanning.Fire", "adf.sample.module.algorithm.SamplePathPlanning");
                    this.clustering = moduleManager.getModule("SampleSearch.Clustering.Fire", "adf.sample.module.algorithm.SampleKMeans");
                } else if(agentURN == POLICE_FORCE) {
                    this.pathPlanning = this.moduleManager.getModule("SampleSearch.PathPlanning.Police", "adf.sample.module.algorithm.SamplePathPlanning");
                    this.clustering = moduleManager.getModule("SampleSearch.Clustering.Police", "adf.sample.module.algorithm.SampleKMeans");
                }
                break;
            case PRECOMPUTED:
                if(agentURN == AMBULANCE_TEAM) {
                    this.pathPlanning = this.moduleManager.getModule("SampleSearch.PathPlanning.Ambulance", "adf.sample.module.algorithm.SamplePathPlanning");
                    this.clustering = moduleManager.getModule("SampleSearch.Clustering.Ambulance", "adf.sample.module.algorithm.SampleKMeans");
                } else if(agentURN == FIRE_BRIGADE) {
                    this.pathPlanning = this.moduleManager.getModule("SampleSearch.PathPlanning.Fire", "adf.sample.module.algorithm.SamplePathPlanning");
                    this.clustering = moduleManager.getModule("SampleSearch.Clustering.Fire", "adf.sample.module.algorithm.SampleKMeans");
                } else if(agentURN == POLICE_FORCE) {
                    this.pathPlanning = this.moduleManager.getModule("SampleSearch.PathPlanning.Police", "adf.sample.module.algorithm.SamplePathPlanning");
                    this.clustering = moduleManager.getModule("SampleSearch.Clustering.Police", "adf.sample.module.algorithm.SampleKMeans");
                }
                break;
            case NON_PRECOMPUTE:
                if(agentURN == AMBULANCE_TEAM) {
                    this.pathPlanning = this.moduleManager.getModule("SampleSearch.PathPlanning.Ambulance", "adf.sample.module.algorithm.SamplePathPlanning");
                    this.clustering = moduleManager.getModule("SampleSearch.Clustering.Ambulance", "adf.sample.module.algorithm.SampleKMeans");
                } else if(agentURN == FIRE_BRIGADE) {
                    this.pathPlanning = this.moduleManager.getModule("SampleSearch.PathPlanning.Fire", "adf.sample.module.algorithm.SamplePathPlanning");
                    this.clustering = moduleManager.getModule("SampleSearch.Clustering.Fire", "adf.sample.module.algorithm.SampleKMeans");
                } else if(agentURN == POLICE_FORCE) {
                    this.pathPlanning = this.moduleManager.getModule("SampleSearch.PathPlanning.Police", "adf.sample.module.algorithm.SamplePathPlanning");
                    this.clustering = moduleManager.getModule("SampleSearch.Clustering.Police", "adf.sample.module.algorithm.SampleKMeans");
                }
                break;
        }
        this.unsearchedBuildingIDs = new HashSet<>();
    }

    @Override
    public Search calc() {
        this.result = null;

        if(this.pathPlanning == null) {
            return this;
        }
        this.pathPlanning.setFrom(this.agentInfo.getPosition());
        this.pathPlanning.setDestination(this.unsearchedBuildingIDs);
        List<EntityID> path = this.pathPlanning.calc().getResult();
        if(path != null && path.size() > 0) {
            this.result = path.get(path.size() - 1);
        }
        return this;
    }

    @Override
    public Search updateInfo(MessageManager messageManager) {
        super.updateInfo(messageManager);
        if(this.getCountUpdateInfo() >= 2) {
            return this;
        }
        this.pathPlanning.updateInfo(messageManager);
        this.clustering.updateInfo(messageManager);

        this.reflectMessage(messageManager);
        this.sendMessage(messageManager);

        if(this.unsearchedBuildingIDs.isEmpty()) {
            this.reset();
        }
        this.unsearchedBuildingIDs.removeAll(this.worldInfo.getChanged().getChangedEntities());

        if(this.unsearchedBuildingIDs.isEmpty()) {
            this.reset();
            this.unsearchedBuildingIDs.removeAll(this.worldInfo.getChanged().getChangedEntities());
        }
        return this;
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    private void reset() {
        this.unsearchedBuildingIDs.clear();

        boolean useClustering = true;
        Collection<StandardEntity> clusterEntities = null;
        if(this.clustering == null) {
            useClustering = false;
        } else {
            int clusterIndex = this.clustering.getClusterIndex(this.agentInfo.getID());
            clusterEntities = this.clustering.getClusterEntities(clusterIndex);
            if(clusterEntities == null || clusterEntities.isEmpty()) {
                useClustering = false;
            }
        }

        if(useClustering) {
            for(StandardEntity entity : clusterEntities) {
                if(entity instanceof Building && entity.getStandardURN() != REFUGE) {
                    this.unsearchedBuildingIDs.add(entity.getID());
                }
            }
        } else {
            this.unsearchedBuildingIDs.addAll(
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
                    if(blockade.isRepairCostDefined() && rollbackBlockade.isRepairCostDefined()) {
                        if (blockade.getRepairCost() != rollbackBlockade.getRepairCost()) {
                            messageManager.addMessage(new MessageRoad(true, road, blockade, false));
                        }
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
        if(this.getCountPrecompute() >= 2) {
            return this;
        }
        this.pathPlanning.precompute(precomputeData);
        this.clustering.precompute(precomputeData);
        return this;
    }

    @Override
    public Search resume(PrecomputeData precomputeData) {
        super.resume(precomputeData);
        if(this.getCountResume() >= 2) {
            return this;
        }
        this.worldInfo.requestRollback();
        this.pathPlanning.resume(precomputeData);
        this.clustering.resume(precomputeData);
        return this;
    }

    @Override
    public Search preparate() {
        super.preparate();
        if(this.getCountPreparate() >= 2) {
            return this;
        }
        this.worldInfo.requestRollback();
        this.pathPlanning.preparate();
        this.clustering.preparate();
        return this;
    }
}