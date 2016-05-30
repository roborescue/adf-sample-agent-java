package adf.sample.extaction;

import adf.agent.action.ambulance.ActionLoad;
import adf.agent.action.ambulance.ActionRescue;
import adf.agent.action.ambulance.ActionUnload;
import adf.agent.action.common.ActionMove;
import adf.agent.action.common.ActionRest;
import adf.agent.info.AgentInfo;
import adf.agent.info.ScenarioInfo;
import adf.agent.info.WorldInfo;
import adf.agent.module.ModuleManager;
import adf.component.extaction.ExtAction;
import adf.component.module.algorithm.PathPlanning;
import rescuecore2.standard.entities.*;
import rescuecore2.worldmodel.EntityID;

import java.util.List;

public class ActionTransport extends ExtAction {

    private Human target;

    public ActionTransport(AgentInfo agentInfo, WorldInfo worldInfo, ScenarioInfo scenarioInfo, ModuleManager moduleManager) {
        super(agentInfo, worldInfo, scenarioInfo, moduleManager);
        this.target = null;
    }

    @Override
    public ExtAction setTarget(EntityID... targets) {
        if(targets != null) {
            StandardEntity entity = this.worldInfo.getEntity(targets[0]);
            if(entity instanceof Human) {
                this.target = (Human)entity;
            }
        }
        return this;
    }

    @Override
    public ExtAction calc() {
        this.result = new ActionRest();
        if(this.target.getPosition().equals(this.agentInfo.getID())) {
            if(agentInfo.getPositionArea().getStandardURN().equals(StandardEntityURN.REFUGE)) {
                this.result = new ActionUnload();
            }
            else {
                PathPlanning pathPlanning = this.moduleManager.getModule("PathPlanning");
                pathPlanning.setFrom(agentInfo.getPosition());
                pathPlanning.setDestination(this.worldInfo.getEntityIDsOfType(StandardEntityURN.REFUGE));
                pathPlanning.calc();
                List<EntityID> path = pathPlanning.getResult();
                if (path != null) {
                    this.result = new ActionMove(path);
                }
            }
        }
        else {
            if (target.getPosition().equals(agentInfo.getPosition())) {
                if ((target instanceof Civilian) && target.getBuriedness() == 0 && !(agentInfo.getPositionArea() instanceof Refuge)) {
                    this.result = new ActionLoad(target.getID());
                } else if (target.getBuriedness() > 0) {
                    this.result = new ActionRescue(target.getID());
                }
            } else {
                PathPlanning pathPlanning = this.moduleManager.getModule("PathPlanning");
                pathPlanning.setFrom(agentInfo.getPosition());
                pathPlanning.setDestination(target.getPosition());
                pathPlanning.calc();
                List<EntityID> path = pathPlanning.getResult();
                if (path != null) {
                    this.result = new ActionMove(path);
                }
            }
        }
        return this;
    }

}
