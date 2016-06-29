package adf.sample.tactics;

import adf.agent.action.Action;
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
import adf.component.module.complex.BuildingSelector;
import adf.component.module.complex.Search;
import adf.component.tactics.TacticsFire;
import adf.sample.SampleModuleKey;
import adf.util.WorldUtil;
import rescuecore2.standard.entities.*;
import rescuecore2.worldmodel.EntityID;

public class SampleFire extends TacticsFire {

    private PathPlanning pathPlanning;
    private BuildingSelector buildingSelector;
    private Search search;
    private Clustering clustering;

    @Override
    public void initialize(AgentInfo agentInfo, WorldInfo worldInfo, ScenarioInfo scenarioInfo, ModuleManager moduleManager, MessageManager messageManager) {
        worldInfo.indexClass(
                StandardEntityURN.ROAD,
                StandardEntityURN.HYDRANT,
                StandardEntityURN.BUILDING,
                StandardEntityURN.REFUGE,
                StandardEntityURN.GAS_STATION,
                StandardEntityURN.AMBULANCE_CENTRE,
                StandardEntityURN.FIRE_STATION,
                StandardEntityURN.POLICE_OFFICE
        );
        //init ExtAction
        moduleManager.getExtAction(SampleModuleKey.FIRE_ACTION_FIREFIGHTING, "adf.sample.extaction.ActionFireFighting");
        moduleManager.getExtAction(SampleModuleKey.FIRE_ACTION_REFILL, "adf.sample.extaction.ActionRefill");
        moduleManager.getExtAction(SampleModuleKey.FIRE_ACTION_SEARCH, "adf.sample.extaction.ActionSearch");
    }

    @Override
    public void precompute(AgentInfo agentInfo, WorldInfo worldInfo, ScenarioInfo scenarioInfo, ModuleManager moduleManager, PrecomputeData precomputeData) {
        this.pathPlanning = moduleManager.getModule(SampleModuleKey.FIRE_MODULE_PATH_PLANNING, "adf.sample.module.algorithm.SamplePathPlanning");
        this.pathPlanning.precompute(precomputeData);
        this.clustering = moduleManager.getModule(SampleModuleKey.FIRE_MODULE_CLUSTERING, "adf.sample.module.algorithm.SampleKMeans");
        this.clustering.precompute(precomputeData);
        this.search = moduleManager.getModule(SampleModuleKey.FIRE_MODULE_SEARCH, "adf.sample.module.complex.SampleSearch");
        this.search.precompute(precomputeData);
        this.buildingSelector = moduleManager.getModule(SampleModuleKey.FIRE_MODULE_BUILDING_SELECTOR, "adf.sample.module.complex.SampleBuildingSelector");
        this.buildingSelector.precompute(precomputeData);
    }

    @Override
    public void resume(AgentInfo agentInfo, WorldInfo worldInfo, ScenarioInfo scenarioInfo, ModuleManager moduleManager, PrecomputeData precomputeData) {
        this.pathPlanning = moduleManager.getModule(SampleModuleKey.FIRE_MODULE_PATH_PLANNING, "adf.sample.module.algorithm.SamplePathPlanning");
        this.pathPlanning.resume(precomputeData);
        this.clustering = moduleManager.getModule(SampleModuleKey.FIRE_MODULE_CLUSTERING, "adf.sample.module.algorithm.SampleKMeans");
        this.clustering.resume(precomputeData);
        this.search = moduleManager.getModule(SampleModuleKey.FIRE_MODULE_SEARCH, "adf.sample.module.complex.SampleSearch");
        this.search.resume(precomputeData);
        this.buildingSelector = moduleManager.getModule(SampleModuleKey.FIRE_MODULE_BUILDING_SELECTOR, "adf.sample.module.complex.SampleBuildingSelector");
        this.buildingSelector.resume(precomputeData);
    }

    @Override
    public void preparate(AgentInfo agentInfo, WorldInfo worldInfo, ScenarioInfo scenarioInfo, ModuleManager moduleManager) {
        this.pathPlanning = moduleManager.getModule(SampleModuleKey.FIRE_MODULE_PATH_PLANNING, "adf.sample.module.algorithm.SamplePathPlanning");
        this.pathPlanning.preparate();
        this.clustering = moduleManager.getModule(SampleModuleKey.FIRE_MODULE_CLUSTERING, "adf.sample.module.algorithm.SampleKMeans");
        this.clustering.preparate();
        this.search = moduleManager.getModule(SampleModuleKey.FIRE_MODULE_SEARCH, "adf.sample.module.complex.SampleSearch");
        this.search.preparate();
        this.buildingSelector = moduleManager.getModule(SampleModuleKey.FIRE_MODULE_BUILDING_SELECTOR, "adf.sample.module.complex.SampleBuildingSelector");
        this.buildingSelector.preparate();
    }

    @Override
    public Action think(AgentInfo agentInfo, WorldInfo worldInfo, ScenarioInfo scenarioInfo, ModuleManager moduleManager, MessageManager messageManager) {
        this.pathPlanning.updateInfo(messageManager);
        this.clustering.updateInfo(messageManager);
        this.buildingSelector.updateInfo(messageManager);
        this.search.updateInfo(messageManager);

        this.updateInfo(worldInfo, messageManager);
        this.sendMessage(worldInfo, messageManager);

        FireBrigade me = (FireBrigade) agentInfo.me();
        //check buriedness
        if(me.isBuriednessDefined() && me.getBuriedness() > 0) {
            this.updateInfo(worldInfo, messageManager);
            messageManager.addMessage(new MessageFireBrigade(true, me, MessageFireBrigade.ACTION_REST, me.getPosition()));
            return new ActionRest();
        }

        // Are we currently filling with water?
        // Are we out of water?
        Action action = moduleManager.getExtAction(SampleModuleKey.FIRE_ACTION_REFILL, "adf.sample.extaction.ActionRefill")
                                     .calc().getAction();
        if(action != null) {
            return action;
        }

        // Find all buildings that are on fire
        EntityID target = this.buildingSelector.calc().getTarget();
        if(target != null) {
            action = moduleManager.getExtAction(SampleModuleKey.FIRE_ACTION_FIREFIGHTING, "adf.sample.extaction.ActionFireFighting")
                                  .setTarget(target).calc().getAction();
            if(action != null) {
                return action;
            }
        }
        // Nothing to do
        target = this.search.calc().getTarget();
        return moduleManager.getExtAction(SampleModuleKey.FIRE_ACTION_SEARCH, "adf.sample.extaction.ActionSearch")
                            .setTarget(target).calc().getAction();
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
            else if(entity instanceof Road) {
                Road road = (Road)entity;
                if(road.isBlockadesDefined() && road.getBlockades().isEmpty()) {
                    messageManager.addMessage(new MessageRoad(true, road, null, true));
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
