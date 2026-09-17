import { createRouter, createWebHistory } from 'vue-router'

import DatasetView from '../views/DatasetView.vue'
import DownloadView from '../views/DownloadView.vue'
import IntegrityView from '../views/IntegrityView.vue'
import NotFoundView from '../views/NotFoundView.vue'

const routes = [
  { path: '/', redirect: { name: 'downloads' } },
  { path: '/downloads', name: 'downloads', component: DownloadView },
  {
    path: '/downloads/tasks/:taskId',
    name: 'download-task',
    components: {
      default: DownloadView,
      task: () => import('../views/DownloadTaskView.vue'),
    },
  },
  { path: '/datasets', name: 'datasets', component: DatasetView },
  { path: '/integrity', name: 'integrity', component: IntegrityView },
  {
    path: '/integrity/checks/:checkId',
    name: 'integrity-check',
    component: () => import('../views/IntegrityCheckView.vue'),
    props: true,
  },
  {
    path: '/settings',
    name: 'settings',
    component: () => import('../views/SettingsView.vue'),
  },
  {
    path: '/:pathMatch(.*)*',
    name: 'not-found',
    component: NotFoundView,
  },
]

export function createAppRouter(history = createWebHistory()) {
  return createRouter({ history, routes })
}

export default createAppRouter()
