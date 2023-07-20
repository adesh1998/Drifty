/** @type {import('next').NextConfig} */
const nextConfig = {
    basePath: "/Drifty",
    assetprifex: "/Drifty",
    output: 'export',
    images: {
        minimumCacheTTL: 60 * 60 * 24,
        remotePatterns: [
            {
                protocol: 'https',
                hostname: 'avatars.githubusercontent.com',
            },
            {
                protocol: 'https',
                hostname: 'camo.githubusercontent.com',
            },
            {
                protocol: 'https',
                hostname: 'cdn.jsdelivr.net',
            }
        ],
        dangerouslyAllowSVG: true,
        unoptimized: true
    },
}

module.exports = nextConfig
