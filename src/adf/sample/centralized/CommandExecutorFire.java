package adf.sample.centralized;

import adf.agent.action.common.ActionMove;
import adf.agent.action.common.ActionRest;
import adf.agent.action.fire.ActionRefill;
import adf.agent.communication.MessageManager;
import adf.agent.communication.standard.bundle.centralized.CommandFire;
import adf.agent.communication.standard.bundle.centralized.MessageReport;
import adf.agent.develop.DevelopData;
import adf.agent.info.AgentInfo;
import adf.agent.info.ScenarioInfo;
import adf.agent.info.WorldInfo;
import adf.agent.module.ModuleManager;
import adf.agent.precompute.PrecomputeData;
import adf.component.centralized.CommandExecutor;
import adf.component.extaction.ExtAction;
import adf.component.module.algorithm.PathPlanning;
import rescuecore2.standard.entities.*;
import rescuecore2.worldmodel.EntityID;

import java.util.Collection;
import java.util.List;
import java.util.Objects;

import static rescuecore2.standard.entities.StandardEntityURN.HYDRANT;
import static rescuecore2.standard.entities.StandardEntityURN.REFUGE;

public class CommandExecutorFire extends CommandExecutor<CommandFire> {
    private static final int ACTION_UNKNOWN = -1;
    private static final int ACTION_REST = CommandFire.ACTION_REST;
    private static final int ACTION_MOVE = CommandFire.ACTION_MOVE;
    private static final int ACTION_EXTINGUISH = CommandFire.ACTION_EXTINGUISH;
    private static final int ACTION_REFILL = CommandFire.ACTION_REFILL;
    private static final int ACTION_AUTONOMY = CommandFire.ACTION_AUTONOMY;

    private PathPlanning pathPlanning;

    private ExtAction actionFireFighting;
    private ExtAction actionExtMove;

    private int maxWater;

    private int commandType;
    private EntityID target;
    private EntityID commanderID;
    
    public CommandExecutorFire(AgentInfo ai, WorldInfo wi, ScenarioInfo si, ModuleManager moduleManager, DevelopData developData) {
        super(ai, wi, si, moduleManager, developData);
        this.maxWater = scenarioInfo.getFireTankMaximum();
        this.commandType = ACTION_UNKNOWN;
        switch  (si.getMode()) {
            case PRECOMPUTATION_PHASE:
                this.pathPlanning = moduleManager.getModule("CommandExecutorFire.PathPlanning", "adf.sample.module.algorithm.SamplePathPlanning");
                this.actionFireFighting = moduleManager.getExtAction("CommandExecutorFire.ActionFireFighting", "adf.sample.extaction.ActionFireFighting");
                this.actionExtMove = moduleManager.getExtAction("CommandExecutorFire.ActionExtMove", "adf.sample.extaction.ActionExtMove");
                break;
            case PRECOMPUTED:
                this.pathPlanning = moduleManager.getModule("CommandExecutorFire.PathPlanning", "adf.sample.module.algorithm.SamplePathPlanning");
                this.actionFireFighting = moduleManager.getExtAction("CommandExecutorFire.ActionFireFighting", "adf.sample.extaction.ActionFireFighting");
                this.actionExtMove = moduleManager.getExtAction("CommandExecutorFire.ActionExtMove", "adf.sample.extaction.ActionExtMove");
                break;
            case NON_PRECOMPUTE:
                this.pathPlanning = moduleManager.getModule("CommandExecutorFire.PathPlanning", "adf.sample.module.algorithm.SamplePathPlanning");
                this.actionFireFighting = moduleManager.getExtAction("CommandExecutorFire.ActionFireFighting", "adf.sample.extaction.ActionFireFighting");
                this.actionExtMove = moduleManager.getExtAction("CommandExecutorFire.ActionExtMove", "adf.sample.extaction.ActionExtMove");
                break;
        }
    }

    @Override
    public CommandExecutor setCommand(CommandFire command) {
        EntityID agentID = this.agentInfo.getID();
        if(command.isToIDDefined() && Objects.requireNonNull(command.getToID()).getValue() == agentID.getValue()) {
            this.commandType = command.getAction();
            this.target = command.getTargetID();
            this.commanderID = command.getSenderID();
        }
        return this;
    }

    public CommandExecutor precompute(PrecomputeData precomputeData) {
        super.precompute(precomputeData);
        if(this.getCountPrecompute() >= 2) {
            return this;
        }
        this.pathPlanning.precompute(precomputeData);
        this.actionFireFighting.precompute(precomputeData);
        this.actionExtMove.precompute(precomputeData);
        return this;
    }

    public CommandExecutor resume(PrecomputeData precomputeData) {
        super.resume(precomputeData);
        if(this.getCountResume() >= 2) {
            return this;
        }
        this.pathPlanning.resume(precomputeData);
        this.actionFireFighting.resume(precomputeData);
        this.actionExtMove.resume(precomputeData);
        return this;
    }

    public CommandExecutor preparate() {
        super.preparate();
        if(this.getCountPreparate() >= 2) {
            return this;
        }
        this.pathPlanning.preparate();
        this.actionFireFighting.preparate();
        this.actionExtMove.preparate();
        return this;
    }

    public CommandExecutor updateInfo(MessageManager messageManager){
        super.updateInfo(messageManager);
        if(this.getCountUpdateInfo() >= 2) {
            return this;
        }
        this.pathPlanning.updateInfo(messageManager);
        this.actionFireFighting.updateInfo(messageManager);
        this.actionExtMove.updateInfo(messageManager);

        if(this.isCommandCompleted()) {
            if(this.commandType != ACTION_UNKNOWN) {
                messageManager.addMessage(new MessageReport(true, true, false, this.commanderID));
                this.commandType = ACTION_UNKNOWN;
                this.target = null;
                this.commanderID = null;
            }
        }
        return this;
    }

    @Override
    public CommandExecutor calc() {
        this.result = null;
        EntityID position = this.agentInfo.getPosition();
        switch (this.commandType) {
            case ACTION_REST:
                if(this.target == null) {
                    Collection<EntityID> refuges = this.worldInfo.getEntityIDsOfType(REFUGE);
                    if(refuges.contains(position)) {
                        this.result = new ActionRest();
                    }else {
                        this.pathPlanning.setFrom(position);
                        this.pathPlanning.setDestination(refuges);
                        List<EntityID> path = this.pathPlanning.calc().getResult();
                        if (path != null && path.size() > 0) {
                            this.result = new ActionMove(path);
                        } else {
                            this.result = new ActionRest();
                        }
                    }
                    return this;
                }
                if (position.getValue() != this.target.getValue()) {
                    List<EntityID> path = this.pathPlanning.getResult(position, this.target);
                    if(path != null && path.size() > 0) {
                        this.result = new ActionMove(path);
                        return this;
                    }
                }
                this.result = new ActionRest();
                return this;
            case ACTION_MOVE:
                if(this.target != null) {
                    this.result = this.actionExtMove.setTarget(this.target).calc().getAction();
                }
                return this;
            case ACTION_EXTINGUISH:
                if(this.target != null) {
                    this.result = this.actionFireFighting.setTarget(this.target).calc().getAction();
                }
                return this;
            case ACTION_REFILL:
                if(this.target == null) {
                    StandardEntityURN positionURN = this.worldInfo.getEntity(position).getStandardURN();
                    if(positionURN == REFUGE) {
                        this.result = new ActionRefill();
                        return this;
                    }
                    this.pathPlanning.setFrom(position);
                    this.pathPlanning.setDestination(this.worldInfo.getEntityIDsOfType(REFUGE));
                    List<EntityID> path = this.pathPlanning.calc().getResult();
                    if(path != null && path.size() > 0) {
                        this.result = new ActionMove(path);
                        return this;
                    }
                    if(positionURN == HYDRANT) {
                        this.result = new ActionRefill();
                        return this;
                    }
                    this.pathPlanning.setFrom(position);
                    this.pathPlanning.setDestination(this.worldInfo.getEntityIDsOfType(HYDRANT));
                    path = this.pathPlanning.calc().getResult();
                    if(path != null && path.size() > 0) {
                        this.result = new ActionMove(path);
                        return this;
                    }
                } else if (position.getValue() != this.target.getValue()) {
                    List<EntityID> path = this.pathPlanning.getResult(position, this.target);
                    if(path != null) {
                        this.result = new ActionMove(path);
                        return this;
                    }
                }
                this.result = new ActionRefill();
                return this;
            case ACTION_AUTONOMY:
                if(this.target != null) {
                    StandardEntity targetEntity = this.worldInfo.getEntity(this.target);
                    if(targetEntity.getStandardURN() == REFUGE) {
                        FireBrigade agent = (FireBrigade) this.agentInfo.me();
                        if(agent.getDamage() > 0) {
                            if (position.getValue() != this.target.getValue()) {
                                List<EntityID> path = this.pathPlanning.getResult(position, this.target);
                                if(path != null) {
                                    this.result = new ActionMove(path);
                                    return this;
                                }
                            }
                            this.result = new ActionRest();
                        } else {
                            this.result = this.actionExtMove.setTarget(this.target).calc().getAction();
                        }
                    } else if (targetEntity instanceof Building) {
                        this.result = this.actionFireFighting.setTarget(this.target).calc().getAction();
                    } else if(targetEntity instanceof Road) {
                        this.result = this.actionExtMove.setTarget(this.target).calc().getAction();
                    }
                }
                return this;
        }
        return this;
    }

    private boolean isCommandCompleted() {
        FireBrigade agent = (FireBrigade) this.agentInfo.me();
        switch (this.commandType) {
            case ACTION_REST:
                if(this.target == null) {
                    return (agent.getDamage() == 0);
                }
                if (this.worldInfo.getEntity(this.target).getStandardURN() == REFUGE) {
                    if (agent.getPosition().getValue() == this.target.getValue()) {
                        return (agent.getDamage() == 0);
                    }
                }
                return false;
            case ACTION_MOVE:
                return this.target == null || (this.agentInfo.getPosition().getValue() == this.target.getValue());
            case ACTION_EXTINGUISH:
                if(this.target == null) {
                    return true;
                }
                Building building = (Building) this.worldInfo.getEntity(this.target);
                if(building.isFierynessDefined()) {
                    return building.getFieryness() >= 4;
                }
                return this.agentInfo.getPosition().getValue() == this.target.getValue();
            case ACTION_REFILL:
                return (((FireBrigade)this.agentInfo.me()).getWater() == this.maxWater);
            case ACTION_AUTONOMY:
                if(this.target != null) {
                    StandardEntity targetEntity = this.worldInfo.getEntity(this.target);
                    if(targetEntity.getStandardURN() == REFUGE) {
                        this.commandType = agent.getDamage() > 0 ? ACTION_REST : ACTION_REFILL;
                        return this.isCommandCompleted();
                    } else if (targetEntity instanceof Building) {
                        this.commandType = ACTION_EXTINGUISH;
                        return this.isCommandCompleted();
                    } else if(targetEntity instanceof Road) {
                        this.commandType = ACTION_MOVE;
                        return this.isCommandCompleted();
                    }
                }
                return true;
        }
        return true;
    }
}
