package adf.sample.tactics;

import adf.agent.action.Action;
import adf.agent.action.common.ActionMove;
import adf.agent.action.common.ActionRest;
import adf.agent.action.police.ActionClear;
import adf.agent.communication.MessageManager;
import adf.agent.communication.standard.bundle.information.MessagePoliceForce;
import adf.agent.develop.DevelopData;
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
import rescuecore2.misc.geometry.GeometryTools2D;
import rescuecore2.misc.geometry.Line2D;
import rescuecore2.misc.geometry.Point2D;
import rescuecore2.standard.entities.*;
import rescuecore2.worldmodel.EntityID;

import java.util.List;

public class SamplePolice extends TacticsPolice {
    private int clearDistance;

    private PathPlanning pathPlanning;
    private RoadSelector roadSelector;
    private Search search;
    private Clustering clustering;

    private RoadSelector taskRoadSelector;
    private Search taskSearch;

    @Override
    public void initialize(AgentInfo agentInfo, WorldInfo worldInfo, ScenarioInfo scenarioInfo, ModuleManager moduleManager, MessageManager messageManager, DevelopData developData) {
        worldInfo.indexClass(
                StandardEntityURN.ROAD,
                StandardEntityURN.HYDRANT,
                StandardEntityURN.BUILDING,
                StandardEntityURN.REFUGE,
                StandardEntityURN.BLOCKADE
        );
        this.clearDistance = scenarioInfo.getClearRepairDistance();
        //init ExtAction
        moduleManager.getExtAction(SampleModuleKey.POLICE_ACTION_EXT_CLEAR, "adf.sample.extaction.ActionExtClear");
        moduleManager.getExtAction(SampleModuleKey.POLICE_ACTION_SEARCH, "adf.sample.extaction.ActionExtMove");
    }

    @Override
    public void precompute(AgentInfo agentInfo, WorldInfo worldInfo, ScenarioInfo scenarioInfo, ModuleManager moduleManager, PrecomputeData precomputeData, DevelopData developData) {
        this.pathPlanning = moduleManager.getModule(SampleModuleKey.POLICE_MODULE_PATH_PLANNING, "adf.sample.module.algorithm.SamplePathPlanning");
        this.pathPlanning.precompute(precomputeData);
        this.clustering = moduleManager.getModule(SampleModuleKey.POLICE_MODULE_CLUSTERING, "adf.sample.module.algorithm.SampleKMeans");
        this.clustering.precompute(precomputeData);
        this.search = moduleManager.getModule(SampleModuleKey.POLICE_MODULE_SEARCH, "adf.sample.module.complex.SampleSearch");
        this.search.precompute(precomputeData);
        this.roadSelector = moduleManager.getModule(SampleModuleKey.POLICE_MODULE_ROAD_SELECTOR, "adf.sample.module.complex.SampleRoadSelector");
        this.roadSelector.precompute(precomputeData);
        this.taskSearch = moduleManager.getModule(SampleModuleKey.POLICE_MODULE_TASK_SEARCH, "adf.sample.module.complex.topdown.SampleTaskSearch");
        this.taskSearch.precompute(precomputeData);
        this.taskRoadSelector = moduleManager.getModule(SampleModuleKey.POLICE_MODULE_TASK_ROAD_SELECTOR, "adf.sample.module.complex.topdown.SampleTaskRoadSelector");
        this.taskRoadSelector.precompute(precomputeData);
    }

    @Override
    public void resume(AgentInfo agentInfo, WorldInfo worldInfo, ScenarioInfo scenarioInfo, ModuleManager moduleManager, PrecomputeData precomputeData, DevelopData developData) {
        this.pathPlanning = moduleManager.getModule(SampleModuleKey.POLICE_MODULE_PATH_PLANNING, "adf.sample.module.algorithm.SamplePathPlanning");
        this.pathPlanning.resume(precomputeData);
        this.clustering = moduleManager.getModule(SampleModuleKey.POLICE_MODULE_CLUSTERING, "adf.sample.module.algorithm.SampleKMeans");
        this.clustering.resume(precomputeData);
        this.search = moduleManager.getModule(SampleModuleKey.POLICE_MODULE_SEARCH, "adf.sample.module.complex.SampleSearch");
        this.search.resume(precomputeData);
        this.roadSelector = moduleManager.getModule(SampleModuleKey.POLICE_MODULE_ROAD_SELECTOR, "adf.sample.module.complex.SampleRoadSelector");
        this.roadSelector.resume(precomputeData);
        this.taskSearch = moduleManager.getModule(SampleModuleKey.POLICE_MODULE_TASK_SEARCH, "adf.sample.module.complex.topdown.SampleTaskSearch");
        this.taskSearch.resume(precomputeData);
        this.taskRoadSelector = moduleManager.getModule(SampleModuleKey.POLICE_MODULE_TASK_ROAD_SELECTOR, "adf.sample.module.complex.topdown.SampleTaskRoadSelector");
        this.taskRoadSelector.resume(precomputeData);
    }

    @Override
    public void preparate(AgentInfo agentInfo, WorldInfo worldInfo, ScenarioInfo scenarioInfo, ModuleManager moduleManager, DevelopData developData) {
        this.pathPlanning = moduleManager.getModule(SampleModuleKey.POLICE_MODULE_PATH_PLANNING, "adf.sample.module.algorithm.SamplePathPlanning");
        this.pathPlanning.preparate();
        this.clustering = moduleManager.getModule(SampleModuleKey.POLICE_MODULE_CLUSTERING, "adf.sample.module.algorithm.SampleKMeans");
        this.clustering.preparate();
        this.search = moduleManager.getModule(SampleModuleKey.POLICE_MODULE_SEARCH, "adf.sample.module.complex.SampleSearch");
        this.search.preparate();
        this.roadSelector = moduleManager.getModule(SampleModuleKey.POLICE_MODULE_ROAD_SELECTOR, "adf.sample.module.complex.SampleRoadSelector");
        this.roadSelector.preparate();
        this.taskSearch = moduleManager.getModule(SampleModuleKey.POLICE_MODULE_TASK_SEARCH, "adf.sample.module.complex.topdown.SampleTaskSearch");
        this.taskSearch.preparate();
        this.taskRoadSelector = moduleManager.getModule(SampleModuleKey.POLICE_MODULE_TASK_ROAD_SELECTOR, "adf.sample.module.complex.topdown.SampleTaskRoadSelector");
        this.taskRoadSelector.preparate();
    }

    @Override
    public Action think(AgentInfo agentInfo, WorldInfo worldInfo, ScenarioInfo scenarioInfo, ModuleManager moduleManager, MessageManager messageManager, DevelopData developData) {
        this.pathPlanning.updateInfo(messageManager);
        this.clustering.updateInfo(messageManager);
        this.roadSelector.updateInfo(messageManager);
        this.search.updateInfo(messageManager);
        this.taskRoadSelector.updateInfo(messageManager);
        this.taskSearch.updateInfo(messageManager);

        PoliceForce me = (PoliceForce) agentInfo.me();
        EntityID target = this.taskSearch.calc().getTarget();
        if(target != null) {
            Action action = moduleManager
                    .getExtAction(SampleModuleKey.POLICE_ACTION_SEARCH)
                    .setTarget(target)
                    .calc().getAction();
            if(action != null) {
                CommunicationMessage message = this.getActionMessage(worldInfo, me, action);
                if(message != null) {
                    messageManager.addMessage(message);
                }
                return action;
            }
        }
        target = this.taskRoadSelector.calc().getTarget();
        if (target == null) {
            target = this.roadSelector.calc().getTarget();
        }
        if(target != null) {
            Action action = moduleManager
                    .getExtAction(SampleModuleKey.POLICE_ACTION_EXT_CLEAR)
                    .setTarget(target)
                    .calc().getAction();
            if(action != null) {
                CommunicationMessage message = this.getActionMessage(worldInfo, me, action);
                if(message != null) {
                    messageManager.addMessage(message);
                }
                return action;
            }
        }
        target = this.search.calc().getTarget();
        if(target != null) {
            Action action = moduleManager
                    .getExtAction(SampleModuleKey.POLICE_ACTION_SEARCH)
                    .setTarget(target)
                    .calc().getAction();
            if(action != null) {
                CommunicationMessage message = this.getActionMessage(worldInfo, me, action);
                if(message != null) {
                    messageManager.addMessage(message);
                }
                return action;
            }
        }

        //check buriedness
        if(me.getBuriedness() > 0) {
            messageManager.addMessage(
                    new MessagePoliceForce(true, me, MessagePoliceForce.ACTION_REST, me.getPosition())
            );
        }
        return new ActionRest();
    }

    private CommunicationMessage getActionMessage(WorldInfo worldInfo, PoliceForce policeForce, Action action) {
        Class<? extends Action> actionClass = action.getClass();
        int actionIndex = -1;
        EntityID target = null;
        if(actionClass == ActionMove.class) {
            List<EntityID> path = ((ActionMove)action).getPath();
            actionIndex = MessagePoliceForce.ACTION_MOVE;
            target = path.get(path.size() - 1);
        } else if(actionClass == ActionClear.class) {
            actionIndex = MessagePoliceForce.ACTION_CLEAR;
            ActionClear ac = (ActionClear)action;
            target = ac.getTarget();
            if(target == null) {
                for(StandardEntity entity : worldInfo.getObjectsInRange(ac.getPosX(), ac.getPosY(), this.clearDistance)) {
                    if(entity.getStandardURN() == StandardEntityURN.BLOCKADE) {
                        if(this.intersect(
                                policeForce.getX(), policeForce.getY(),
                                ac.getPosX(), ac.getPosY(),
                                (Blockade)entity
                        )) {
                            target = entity.getID();
                            break;
                        }
                    }
                }
            }
        } else if(actionClass == ActionRest.class) {
            actionIndex = MessagePoliceForce.ACTION_REST;
            target = policeForce.getPosition();
        }
        if(actionIndex != -1) {
            return new MessagePoliceForce(true, policeForce, actionIndex, target);
        }
        return null;
    }

    private boolean intersect(double agentX, double agentY, double pointX, double pointY, Blockade blockade) {
        List<Line2D> lines = GeometryTools2D.pointsToLines(GeometryTools2D.vertexArrayToPoints(blockade.getApexes()), true);
        for(Line2D line : lines) {
            Point2D start = line.getOrigin();
            Point2D end = line.getEndPoint();
            double startX = start.getX();
            double startY = start.getY();
            double endX = end.getX();
            double endY = end.getY();
            if(java.awt.geom.Line2D.linesIntersect(
                    agentX, agentY, pointX, pointY,
                    startX, startY, endX, endY
            )) {
                double midX = (startX + endX) / 2;
                double midY = (startY + endY) / 2;
                if(!equalsPoint(pointX, pointY, midX, midY) && !equalsPoint(agentX, agentY, midX, midY)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean equalsPoint(double p1X, double p1Y, double p2X, double p2Y) {
        return this.equalsPoint(p1X, p1Y, p2X, p2Y, 1.0D);
    }

    private boolean equalsPoint(double p1X, double p1Y, double p2X, double p2Y, double range) {
        return (p2X - range < p1X && p1X < p2X + range) && (p2Y - range < p1Y && p1Y < p2Y + range);
    }
}
