export function getRouteTitle(route) {
  const title = route.meta?.title
  return (typeof title === 'function' ? title() : title) || ''
}
