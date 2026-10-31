import { ChangeDetectionStrategy, Component, ElementRef, effect, inject, input } from '@angular/core';
import {
  Activity,
  ArrowLeftRight,
  ArrowRight,
  Bell,
  BookOpen,
  Boxes,
  Bug,
  Calendar,
  ChartBar,
  ChartColumn,
  Check,
  ChevronDown,
  ChevronLeft,
  ChevronRight,
  CircleCheck,
  CircleDot,
  Clock,
  ClockFading,
  Code,
  Copy,
  Cpu,
  Crosshair,
  Database,
  Download,
  ExternalLink,
  Eye,
  FileDown,
  FileJson,
  FileText,
  Flag,
  FlaskConical,
  Funnel,
  Gauge,
  GitCompare,
  Globe,
  HelpCircle,
  Hourglass,
  Inbox,
  Info,
  KeyRound,
  Layers3,
  LayoutDashboard,
  Lightbulb,
  ListChecks,
  ListTree,
  LoaderCircle,
  Lock,
  LogOut,
  Map as MapIcon,
  Moon,
  Network,
  Percent,
  Play,
  Radar,
  RefreshCw,
  Rocket,
  Route,
  ScanSearch,
  Search,
  Server,
  Settings,
  Shield,
  ShieldAlert,
  ShieldCheck,
  Sparkle,
  Split,
  Sun,
  Telescope,
  Terminal,
  Timer,
  Trash2,
  TriangleAlert,
  Upload,
  User,
  Waypoints,
  Workflow,
  Wrench,
  X,
  XCircle,
  Zap,
} from 'lucide';

type IconNode = [tag: string, attrs: Record<string, string | number>][];

/** Ícones Lucide usados na aplicação (importação nomeada para tree-shaking). */
const ICONS: Record<string, IconNode> = {
  activity: Activity, 'arrow-left-right': ArrowLeftRight, 'arrow-right': ArrowRight, bell: Bell,
  book: BookOpen, boxes: Boxes, bug: Bug, calendar: Calendar, 'chart-bar': ChartBar, chart: ChartColumn,
  check: Check, 'chevron-down': ChevronDown, 'chevron-left': ChevronLeft, 'chevron-right': ChevronRight,
  'circle-check': CircleCheck, 'circle-dot': CircleDot, clock: Clock, history: ClockFading, code: Code,
  copy: Copy, cpu: Cpu, crosshair: Crosshair, database: Database, download: Download,
  'external-link': ExternalLink, eye: Eye, 'file-down': FileDown, 'file-json': FileJson, 'file-text': FileText,
  flag: Flag, flask: FlaskConical, filter: Funnel, gauge: Gauge, compare: GitCompare, globe: Globe,
  help: HelpCircle, hourglass: Hourglass, inbox: Inbox, info: Info, key: KeyRound, layers: Layers3,
  dashboard: LayoutDashboard, lightbulb: Lightbulb, 'list-checks': ListChecks, 'list-tree': ListTree,
  loader: LoaderCircle, lock: Lock, logout: LogOut, map: MapIcon, moon: Moon, network: Network,
  percent: Percent, play: Play, radar: Radar, refresh: RefreshCw, rocket: Rocket, route: Route,
  'scan-search': ScanSearch, search: Search, server: Server, settings: Settings, shield: Shield,
  'shield-alert': ShieldAlert, 'shield-check': ShieldCheck, sparkle: Sparkle, split: Split, sun: Sun,
  telescope: Telescope, terminal: Terminal, timer: Timer, trash: Trash2, alert: TriangleAlert, upload: Upload,
  user: User, waypoints: Waypoints, workflow: Workflow, wrench: Wrench, x: X, 'x-circle': XCircle, zap: Zap,
} as unknown as Record<string, IconNode>;

const SVG_NS = 'http://www.w3.org/2000/svg';

/**
 * Renderiza um ícone Lucide como SVG inline (sem innerHTML), herdando a cor do texto.
 */
@Component({
  selector: 'el-icon',
  template: '',
  styles: ':host { display: inline-flex; align-items: center; justify-content: center; flex-shrink: 0; line-height: 0; }',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class IconComponent {
  readonly name = input.required<string>();
  readonly size = input<number>(18);
  readonly strokeWidth = input<number>(2);

  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);

  constructor() {
    effect(() => {
      const element = this.host.nativeElement;
      element.replaceChildren();
      const node = ICONS[this.name()] ?? ICONS['help'];
      const svg = document.createElementNS(SVG_NS, 'svg');
      const size = String(this.size());
      svg.setAttribute('width', size);
      svg.setAttribute('height', size);
      svg.setAttribute('viewBox', '0 0 24 24');
      svg.setAttribute('fill', 'none');
      svg.setAttribute('stroke', 'currentColor');
      svg.setAttribute('stroke-width', String(this.strokeWidth()));
      svg.setAttribute('stroke-linecap', 'round');
      svg.setAttribute('stroke-linejoin', 'round');
      svg.setAttribute('aria-hidden', 'true');
      for (const [tag, attrs] of node) {
        const child = document.createElementNS(SVG_NS, tag);
        Object.entries(attrs).forEach(([key, value]) => child.setAttribute(key, String(value)));
        svg.appendChild(child);
      }
      element.appendChild(svg);
    });
  }
}
