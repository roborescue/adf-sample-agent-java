package adf.sample.tactics;

import adf.agent.action.Action;
import adf.agent.action.fire.ActionRescue;
import adf.agent.action.common.ActionMove;
import adf.agent.action.common.ActionRest;
import adf.agent.communication.MessageManager;
import adf.agent.communication.standard.bundle.centralized.CommandFire;
import adf.agent.communication.standard.bundle.centralized.CommandScout;
import adf.agent.communication.standard.bundle.information.MessageFireBrigade;
import adf.agent.develop.DevelopData;
import adf.agent.info.AgentInfo;
import adf.agent.info.ScenarioInfo;
import adf.agent.info.WorldInfo;
import adf.agent.module.ModuleManager;
import adf.agent.precompute.PrecomputeData;
import adf.debug.WorldViewLauncher;
import adf.component.centralized.CommandExecutor;
import adf.component.communication.CommunicationMessage;
import adf.component.extaction.ExtAction;
import adf.component.module.complex.HumanDetector;
import adf.component.module.complex.Search;
import adf.component.tactics.TacticsFireBrigade;
import adf.sample.tactics.utils.MessageTool;
import java.util.List;
import java.util.Objects;
import rescuecore2.standard.entities.FireBrigade;
import rescuecore2.standard.entities.StandardEntityURN;
import rescuecore2.worldmodel.EntityID;

public class SampleTacticsFireBrigade extends TacticsFireBrigade {

  private HumanDetector                 humanDetector;
  private Search                        search;

  private ExtAction                     actionFireRescue;
  private ExtAction                     actionExtMove;

  private CommandExecutor<CommandFire>  commandExecutorFire;
  private CommandExecutor<CommandScout> commandExecutorScout;

  private MessageTool                   messageTool;

  private CommunicationMessage          recentCommand;
  private Boolean                       isVisualDebug;


  @Override
  public void initialize( AgentInfo agentInfo, WorldInfo worldInfo,
      ScenarioInfo scenarioInfo, ModuleManager moduleManager,
      MessageManager messageManager, DevelopData developData ) {
    messageManager.setChannelSubscriber( moduleManager.getChannelSubscriber(
        "MessageManager.PlatoonChannelSubscriber",
        "adf.sample.module.comm.SampleChannelSubscriber" ) );
    messageManager.setMessageCoordinator( moduleManager.getMessageCoordinator(
        "MessageManager.PlatoonMessageCoordinator",
        "adf.sample.module.comm.SampleMessageCoordinator" ) );

    worldInfo.indexClass( StandardEntityURN.CIVILIAN,
        StandardEntityURN.FIRE_BRIGADE, StandardEntityURN.POLICE_FORCE,
        StandardEntityURN.AMBULANCE_TEAM, StandardEntityURN.ROAD,
        StandardEntityURN.HYDRANT, StandardEntityURN.BUILDING,
        StandardEntityURN.REFUGE, StandardEntityURN.GAS_STATION,
        StandardEntityURN.AMBULANCE_CENTRE, StandardEntityURN.FIRE_STATION,
        StandardEntityURN.POLICE_OFFICE );

    this.messageTool = new MessageTool( scenarioInfo, developData );

    this.isVisualDebug = ( scenarioInfo.isDebugMode() && moduleManager
        .getModuleConfig().getBooleanValue( "VisualDebug", false ) );

    this.recentCommand = null;

    // init Algorithm Module & ExtAction
    switch ( scenarioInfo.getMode() ) {
      case PRECOMPUTATION_PHASE:
      case PRECOMPUTED:
        this.humanDetector = moduleManager.getModule(
            "TacticsFireBrigade.HumanDetector",
            "adf.sample.module.complex.SampleHumanDetector" );
        this.search = moduleManager.getModule( "TacticsFireBrigade.Search",
            "adf.sample.module.complex.SampleSearch" );
        this.actionFireRescue = moduleManager.getExtAction(
            "TacticsFireBrigade.ActionFireRescue",
            "adf.sample.extaction.ActionFireRescue" );
        this.actionExtMove = moduleManager.getExtAction(
            "TacticsFireBrigade.ActionExtMove",
            "adf.sample.extaction.ActionExtMove" );
        this.commandExecutorFire = moduleManager.getCommandExecutor(
            "TacticsFireBrigade.CommandExecutorFire",
            "adf.sample.centralized.CommandExecutorFire" );
        this.commandExecutorScout = moduleManager.getCommandExecutor(
            "TacticsFireBrigade.CommandExecutorScout",
            "adf.sample.centralized.CommandExecutorScout" );
        break;
      case NON_PRECOMPUTE:
        this.humanDetector = moduleManager.getModule(
            "TacticsFireBrigade.HumanDetector",
            "adf.sample.module.complex.SampleHumanDetector" );
        this.search = moduleManager.getModule( "TacticsFireBrigade.Search",
            "adf.sample.module.complex.SampleSearch" );
        this.actionFireRescue = moduleManager.getExtAction(
            "TacticsFireBrigade.ActionFireRescue",
            "adf.sample.extaction.ActionFireRescue" );
        this.actionExtMove = moduleManager.getExtAction(
            "TacticsFireBrigade.ActionExtMove",
            "adf.sample.extaction.ActionExtMove" );
        this.commandExecutorFire = moduleManager.getCommandExecutor(
            "TacticsFireBrigade.CommandExecutorFire",
            "adf.sample.centralized.CommandExecutorFire" );
        this.commandExecutorScout = moduleManager.getCommandExecutor(
            "TacticsFireBrigade.CommandExecutorScout",
            "adf.sample.centralized.CommandExecutorScout" );
    }
    registerModule( this.humanDetector );
    registerModule( this.search );
    registerModule( this.actionFireRescue );
    registerModule( this.actionExtMove );
    registerModule( this.commandExecutorFire );
    registerModule( this.commandExecutorScout );
  }


  @Override
  public void precompute( AgentInfo agentInfo, WorldInfo worldInfo,
      ScenarioInfo scenarioInfo, ModuleManager moduleManager,
      PrecomputeData precomputeData, DevelopData developData ) {
    modulesPrecompute( precomputeData );
  }


  @Override
  public void resume( AgentInfo agentInfo, WorldInfo worldInfo,
      ScenarioInfo scenarioInfo, ModuleManager moduleManager,
      PrecomputeData precomputeData, DevelopData developData ) {
    modulesResume( precomputeData );

    if ( isVisualDebug ) {
      WorldViewLauncher.getInstance().showTimeStep( agentInfo, worldInfo,
          scenarioInfo );
    }
  }


  @Override
  public void preparate( AgentInfo agentInfo, WorldInfo worldInfo,
      ScenarioInfo scenarioInfo, ModuleManager moduleManager,
      DevelopData developData ) {
    modulesPreparate();

    if ( isVisualDebug ) {
      WorldViewLauncher.getInstance().showTimeStep( agentInfo, worldInfo,
          scenarioInfo );
    }
  }


  @Override
  public Action think( AgentInfo agentInfo, WorldInfo worldInfo,
      ScenarioInfo scenarioInfo, ModuleManager moduleManager,
      MessageManager messageManager, DevelopData developData ) {
    this.messageTool.reflectMessage( agentInfo, worldInfo, scenarioInfo,
        messageManager );
    this.messageTool.sendRequestMessages( agentInfo, worldInfo, scenarioInfo,
        messageManager );
    this.messageTool.sendInformationMessages( agentInfo, worldInfo,
        scenarioInfo, messageManager );

    modulesUpdateInfo( messageManager );

    if ( isVisualDebug ) {
      WorldViewLauncher.getInstance().showTimeStep( agentInfo, worldInfo,
          scenarioInfo );
    }
    FireBrigade agent = (FireBrigade) agentInfo.me();
    EntityID agentID = agentInfo.getID();
    // command
    for ( CommunicationMessage message : messageManager
        .getReceivedMessageList( CommandScout.class ) ) {
      CommandScout command = (CommandScout) message;
      if ( command.isToIDDefined()
          && Objects.requireNonNull( command.getToID() ).getValue() == agentID
              .getValue() ) {
        this.recentCommand = command;
        this.commandExecutorScout.setCommand( command );
      }
    }
    for ( CommunicationMessage message : messageManager
        .getReceivedMessageList( CommandFire.class ) ) {
      CommandFire command = (CommandFire) message;
      if ( command.isToIDDefined()
          && Objects.requireNonNull( command.getToID() ).getValue() == agentID
              .getValue() ) {
        this.recentCommand = command;
        this.commandExecutorFire.setCommand( command );
      }
    }
    if ( this.recentCommand != null ) {
      Action action = null;
      if ( this.recentCommand.getClass() == CommandFire.class ) {
        action = this.commandExecutorFire.calc().getAction();
      } else if ( this.recentCommand.getClass() == CommandScout.class ) {
        action = this.commandExecutorScout.calc().getAction();
      }
      if ( action != null ) {
        this.sendActionMessage( messageManager, agent, action );
        return action;
      }
    }
    // autonomous
    EntityID target = this.humanDetector.calc().getTarget();
    Action action = this.actionFireRescue.setTarget( target ).calc()
        .getAction();
    if ( action != null ) {
      this.sendActionMessage( messageManager, agent, action );
      return action;
    }
    target = this.search.calc().getTarget();
    action = this.actionExtMove.setTarget( target ).calc().getAction();
    if ( action != null ) {
      this.sendActionMessage( messageManager, agent, action );
      return action;
    }

    messageManager.addMessage( new MessageFireBrigade( true, agent,
        MessageFireBrigade.ACTION_REST, agent.getPosition() ) );
    return new ActionRest();
  }


  private void sendActionMessage( MessageManager messageManager,
      FireBrigade fireBrigade, Action action ) {
    Class<? extends Action> actionClass = action.getClass();
    int actionIndex = -1;
    EntityID target = null;
    if ( actionClass == ActionMove.class ) {
      actionIndex = MessageFireBrigade.ACTION_MOVE;
      List<EntityID> path = ( (ActionMove) action ).getPath();
      if ( path.size() > 0 ) {
        target = path.get( path.size() - 1 );
      }
    } else if ( actionClass == ActionRescue.class ) {
      actionIndex = MessageFireBrigade.ACTION_RESCUE;
      target = ( (ActionRescue) action ).getTarget();
    } else if ( actionClass == ActionRest.class ) {
      actionIndex = MessageFireBrigade.ACTION_REST;
      target = fireBrigade.getPosition();
    }
    if ( actionIndex != -1 ) {
      messageManager.addMessage(
          new MessageFireBrigade( true, fireBrigade, actionIndex, target ) );
    }
  }
}