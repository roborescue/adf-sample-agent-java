package adf.sample.tactics;

import adf.agent.action.Action;
import adf.agent.action.common.ActionMove;
import adf.agent.action.common.ActionRest;
import adf.agent.communication.MessageManager;
import adf.agent.communication.standard.bundle.information.*;
import adf.agent.info.AgentInfo;
import adf.agent.info.ScenarioInfo;
import adf.agent.info.WorldInfo;
import adf.agent.module.ModuleManager;
import adf.agent.precompute.PrecomputeData;
import adf.component.communication.CommunicationMessage;
import adf.component.module.algorithm.Clustering;
import adf.component.module.algorithm.PathPlanning;
import adf.component.module.complex.RoadSelector;
import adf.component.module.complex.Search;
import adf.component.tactics.TacticsPolice;
import adf.sample.SampleModuleKey;
import adf.util.WorldUtil;
import rescuecore2.standard.entities.*;
import rescuecore2.worldmodel.EntityID;

import java.util.Collection;
import java.util.List;

public class SamplePolice extends TacticsPolice {

    private PathPlanning pathPlanning;
    private RoadSelector roadSelector;
    private Search search;
    private Clustering clustering;

    private int clusterIndex;

    @Override
    public void initialize(AgentInfo agentInfo, WorldInfo worldInfo, ScenarioInfo scenarioInfo, ModuleManager moduleManager, MessageManager messageManager) {
        worldInfo.indexClass(
                StandardEntityURN.ROAD,
                StandardEntityURN.HYDRANT,
                StandardEntityURN.BUILDING,
                StandardEntityURN.REFUGE,
                StandardEntityURN.BLOCKADE
        );
        this.clusterIndex = -1;
        //init ExtAction
        moduleManager.getExtAction(SampleModuleKey.POLICE_ACTION_EXT_CLEAR, "adf.sample.extaction.ActionExtClear");
        moduleManager.getExtAction(SampleModuleKey.POLICE_ACTION_SEARCH, "adf.sample.extaction.ActionSearch");
    }

    @Override
    public void precompute(AgentInfo agentInfo, WorldInfo worldInfo, ScenarioInfo scenarioInfo, ModuleManager moduleManager, PrecomputeData precomputeData) {
        this.pathPlanning = moduleManager.getModule(SampleModuleKey.POLICE_MODULE_PATH_PLANNING, "adf.sample.module.algorithm.SamplePathPlanning");
        this.pathPlanning.precompute(precomputeData);
        this.clustering = moduleManager.getModule(SampleModuleKey.POLICE_MODULE_CLUSTERING, "adf.sample.module.algorithm.SampleKMeans");
        this.clustering.precompute(precomputeData);
        this.search = moduleManager.getModule(SampleModuleKey.POLICE_MODULE_SEARCH, "adf.sample.module.complex.SampleSearch");
        this.search.precompute(precomputeData);
        this.roadSelector = moduleManager.getModule(SampleModuleKey.POLICE_MODULE_ROAD_SELECTOR, "adf.sample.module.complex.SampleRoadSelector");
        this.roadSelector.precompute(precomputeData);
    }

    @Override
    public void resume(AgentInfo agentInfo, WorldInfo worldInfo, ScenarioInfo scenarioInfo, ModuleManager moduleManager, PrecomputeData precomputeData) {
        this.pathPlanning = moduleManager.getModule(SampleModuleKey.POLICE_MODULE_PATH_PLANNING, "adf.sample.module.algorithm.SamplePathPlanning");
        this.pathPlanning.resume(precomputeData);
        this.clustering = moduleManager.getModule(SampleModuleKey.POLICE_MODULE_CLUSTERING, "adf.sample.module.algorithm.SampleKMeans");
        this.clustering.resume(precomputeData);
        this.search = moduleManager.getModule(SampleModuleKey.POLICE_MODULE_SEARCH, "adf.sample.module.complex.SampleSearch");
        this.search.resume(precomputeData);
        this.roadSelector = moduleManager.getModule(SampleModuleKey.POLICE_MODULE_ROAD_SELECTOR, "adf.sample.module.complex.SampleRoadSelector");
        this.roadSelector.resume(precomputeData);
    }

    @Override
    public void preparate(AgentInfo agentInfo, WorldInfo worldInfo, ScenarioInfo scenarioInfo, ModuleManager moduleManager) {
        this.pathPlanning = moduleManager.getModule(SampleModuleKey.POLICE_MODULE_PATH_PLANNING, "adf.sample.module.algorithm.SamplePathPlanning");
        this.pathPlanning.preparate();
        this.clustering = moduleManager.getModule(SampleModuleKey.POLICE_MODULE_CLUSTERING, "adf.sample.module.algorithm.SampleKMeans");
        this.clustering.preparate();
        this.search = moduleManager.getModule(SampleModuleKey.POLICE_MODULE_SEARCH, "adf.sample.module.complex.SampleSearch");
        this.search.preparate();
        this.roadSelector = moduleManager.getModule(SampleModuleKey.POLICE_MODULE_ROAD_SELECTOR, "adf.sample.module.complex.SampleRoadSelector");
        this.roadSelector.preparate();
    }

    @Override
    public Action think(AgentInfo agentInfo, WorldInfo worldInfo, ScenarioInfo scenarioInfo, ModuleManager moduleManager, MessageManager messageManager) {
        this.pathPlanning.updateInfo(messageManager);
        this.clustering.updateInfo(messageManager);
        this.roadSelector.updateInfo(messageManager);
        this.search.updateInfo(messageManager);

        this.updateInfo(worldInfo, messageManager);
        this.sendMessage(worldInfo, messageManager);

        PoliceForce me = (PoliceForce) agentInfo.me();
        //check buriedness
        if(me.isBuriednessDefined() && me.getBuriedness() > 0) {
            this.updateInfo(worldInfo, messageManager);
            messageManager.addMessage(new MessagePoliceForce(true, me, MessagePoliceForce.ACTION_REST, me.getPosition()));
            return new ActionRest();
        }

        if(this.clusterIndex == -1) {
            this.clusterIndex = this.clustering.getClusterIndex(agentInfo.getID());
        }

        EntityID target = this.roadSelector.calc().getTarget();
        if(target != null) {
            Action action = moduleManager.getExtAction(SampleModuleKey.POLICE_ACTION_EXT_CLEAR).setTarget(target).calc().getAction();
            if(action != null) {
                return action;
            }
        }

        // Nothing to do
        target = this.search.calc().getTarget();
        Action action = moduleManager.getExtAction(SampleModuleKey.POLICE_ACTION_SEARCH).setTarget(target).calc().getAction();
        if(action != null) {
            return action;
        }
        Collection<StandardEntity> list = this.clustering.getClusterEntities(this.clusterIndex);
        if(!list.contains(agentInfo.me())) {
            List<EntityID> path =
                    this.pathPlanning.setFrom(agentInfo.getPosition()).setDestination(WorldUtil.convertToID(list)).getResult();
            if (path != null) {
                return new ActionMove(path);
            }
        }
        return new ActionRest();
    }

    private void sendMessage(WorldInfo worldInfo, MessageManager messageManager) {
        for(EntityID id : worldInfo.getChanged().getChangedEntities()) {
            StandardEntity entity = worldInfo.getEntity(id);
            if(entity.getStandardURN() == StandardEntityURN.CIVILIAN) {
                Civilian civilian = (Civilian)entity;
                if(this.needSend(worldInfo, civilian)) {
                    messageManager.addMessage(new MessageCivilian(true, civilian));
                }
            }
            else if(entity instanceof Building) {
                Building building = (Building)entity;
                if(building.isFierynessDefined()) {
                    if(building.getFieryness() >= 1 && building.getFieryness() <= 3) {
                        messageManager.addMessage(new MessageBuilding(true, building));
                    }
                }
            }
        }
    }

    private boolean needSend(WorldInfo worldInfo, Civilian civilian) {
        if(civilian.isBuriednessDefined() && civilian.getBuriedness() > 0) {
            return true;
        }
        else if(civilian.isDamageDefined() && civilian.getDamage() > 0 && civilian.isPositionDefined() && worldInfo.getEntity(civilian.getPosition()) instanceof Road){
            return true;
        }
        return false;
    }

    private void updateInfo(WorldInfo worldInfo, MessageManager messageManager) {
        for(CommunicationMessage message : messageManager.getReceivedMessageList()) {
            if(message.getClass() == MessageCivilian.class) {
                MessageCivilian mc = (MessageCivilian) message;
                if(!worldInfo.getChanged().getChangedEntities().contains(mc.getAgentID())) WorldUtil.reflectMessage(worldInfo, mc);
            }
            else if(message.getClass() == MessageBuilding.class) {
                MessageBuilding mb = (MessageBuilding)message;
                if(!worldInfo.getChanged().getChangedEntities().contains(mb.getBuildingID())) WorldUtil.reflectMessage(worldInfo, mb);
            }
            else if(message.getClass() == MessageAmbulanceTeam.class) {
                MessageAmbulanceTeam mat = (MessageAmbulanceTeam)message;
                if(!worldInfo.getChanged().getChangedEntities().contains(mat.getAgentID())) WorldUtil.reflectMessage(worldInfo, mat);
                if(mat.getAction() == MessageAmbulanceTeam.ACTION_RESCUE) {
                    StandardEntity entity = worldInfo.getEntity(mat.getTargetID());
                    if(entity != null && entity instanceof Human) {
                        Human human = (Human)entity;
                        if(!human.isPositionDefined()) {
                            human.setPosition(mat.getPosition());
                        }
                    }
                } else if(mat.getAction() == MessageAmbulanceTeam.ACTION_LOAD) {
                    StandardEntity entity = worldInfo.getEntity(mat.getTargetID());
                    if(entity != null && entity instanceof Civilian) {
                        Civilian civilian = (Civilian)entity;
                        if(!civilian.isPositionDefined()) {
                            civilian.setPosition(mat.getAgentID());
                        }
                    }
                }
            }
            else if(message.getClass() == MessageFireBrigade.class) {
                MessageFireBrigade mfb = (MessageFireBrigade) message;
                if(!worldInfo.getChanged().getChangedEntities().contains(mfb.getAgentID())) WorldUtil.reflectMessage(worldInfo, mfb);
            }
            else if(message.getClass() == MessagePoliceForce.class) {
                MessagePoliceForce mpf = (MessagePoliceForce) message;
                if(!worldInfo.getChanged().getChangedEntities().contains(mpf.getAgentID())) WorldUtil.reflectMessage(worldInfo, mpf);
            }
        }
    }
}
