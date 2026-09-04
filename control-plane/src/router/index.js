import { createRouter, createWebHistory } from 'vue-router'

import DatasetView from '../views/DatasetView.vue'
import DownloadView from '../views/DownloadView.vue'
import NotFoundView from '../views/NotFoundView.vue'

const routes = [
  { path: '/', redirect: { name: 'downloads' } },
  { path: '/downloads', name: 'downloads', component: DownloadView },
  { path: '/datasets', name: 'datasets', component: DatasetView },
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
